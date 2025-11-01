package com.nineforce.service.tool.vmlease;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.compute.v1.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.time.Duration;

@Service
@Slf4j
public class GcpVmService {
    public static final String PROJECT_ID = "eng-empire-470108-k1";

    // ─── Cloudflare (kept in code as requested) ───────────────────────────
    private static final String CLOUDFLARE_API_TOKEN = "ufYrWE7ZjMVcA11YrBsYZyXRYBENTBKevrffN9Ii";
    private static final String CLOUDFLARE_ZONE_ID   = "1b177330b2d32c95cfe70bbb17369e17";
    private static final String DOMAIN_SUFFIX        = ".nineforce.com";
    // Let tests override the base URL: -Dcloudflare.base-url=http://localhost:NNNN
    private static final String CF_BASE = System.getProperty(
            "cloudflare.base-url", "https://api.cloudflare.com/client/v4"
    );

    // Reusable HTTP client & JSON mapper
    private final HttpClient http = HttpClient.newBuilder()
             .connectTimeout(Duration.ofSeconds(15))
             .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /* ------------------------------------------------------------------ */
    /* PUBLIC API                                                         */
    /* ------------------------------------------------------------------ */

    public List<Map<String, String>> listAllInstances() throws IOException {
        List<Map<String, String>> out = new ArrayList<>();
        try (InstancesClient instancesClient = InstancesClient.create()) {
            AggregatedListInstancesRequest req = AggregatedListInstancesRequest.newBuilder()
                    .setProject(PROJECT_ID)
                    .build();
            InstancesClient.AggregatedListPagedResponse resp = instancesClient.aggregatedList(req);
            for (Map.Entry<String, InstancesScopedList> e : resp.iterateAll()) {
                String zone = e.getKey().replace("zones/", "");
                InstancesScopedList scoped = e.getValue();
                for (Instance inst : scoped.getInstancesList()) {
                    Map<String,String> m = new HashMap<>();
                    m.put("name", inst.getName());
                    m.put("zone", zone);
                    m.put("status", inst.getStatus());
                    if (!inst.getNetworkInterfacesList().isEmpty()) {
                        var ni = inst.getNetworkInterfaces(0);
                        m.put("privateIP", ni.getNetworkIP());
                        if (!ni.getAccessConfigsList().isEmpty()) {
                            m.put("publicIP", ni.getAccessConfigs(0).getNatIP());
                        }
                    }
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** Powers ON the VM, updates DNS if it has a public IP, and returns details. */
    public Map<String, String> startVm(String zone, String vmName) throws IOException {
        try (InstancesClient client = InstancesClient.create()) {
            Operation op = client.startAsync(PROJECT_ID, zone, vmName).get();   //get() blocks until done
            if (op.hasError()) {
                throw new IOException("Start failed: " + op.getError());
            }
            Map<String,String> details = describeVm(client, zone, vmName);

            // DNS update (only if public IP exists)
            String ip = details.get("publicIP");
            String dnsResult = "No update performed";
            if (ip != null && !ip.isBlank()) {
                dnsResult = updateCloudflareDNS(vmName, ip);
                details.put("dnsResult", dnsResult);
            } else {
                log.info("No public IP for {}, skipping DNS update", vmName);
            }
            details.put("dnsResult", dnsResult);   // <-- always present
            return details;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for start", ie);
        } catch (ExecutionException | ApiException ee) {
            throw new IOException("RPC failed: " + rootMessage(ee), ee.getCause() == null ? ee : ee.getCause());
        }
    }

    /** Powers **OFF** the VM and returns its final state (for logging). */
    public Map<String, String> stopVm(String zone, String vmName) throws IOException {
        try (InstancesClient client = InstancesClient.create()) {
            Operation op = client.stopAsync(PROJECT_ID, zone, vmName).get(); //get() blocks until done
            if (op.hasError()) {
                throw new IOException("Stop failed: " + op.getError());
            }
            return describeVm(client, zone, vmName);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for stop", ie);
        } catch (ExecutionException | ApiException ee) {
            throw new IOException("RPC failed: " + rootMessage(ee), ee.getCause() == null ? ee : ee.getCause());
        }

    }

    /** Stops all RUNNING VMs across the project. */
    public void stopAllVm() throws IOException {
        var instances = listAllInstances();
        var running = instances.stream()
                .filter(m -> "RUNNING".equalsIgnoreCase(m.getOrDefault("status", "")))
                .toList();

        if (running.isEmpty()) {
            log.info("No RUNNING instances to stop.");
            return;
        }

        int poolSize = Math.min(10, running.size());

        @SuppressWarnings("resource") // IntelliJ: we shut it down manually below
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {

            for (var inst : running) {
                final String zone = inst.get("zone");
                final String name = inst.get("name");
                pool.submit(() -> {
                    try {
                        stopVm(zone, name);
                        log.info("Stopped instance: {} in {}", name, zone);
                    } catch (IOException e) {
                        log.error("Failed to stop {} in {}: {}", name, zone, e.getMessage(), e);
                    }
                });
            }

            pool.shutdown();
            if (!pool.awaitTermination(60, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }
    }

    public String setProxyIp(String publicIP) { return updateCloudflareDNS("proxy", publicIP); }

    public String getProxyIp() {
        return getARecordContent("proxy"); // or keep your existing GET logic public
    }


    /* ------------------------------------------------------------------ */
    /* INTERNAL helpers                                                   */
    /* ------------------------------------------------------------------ */

    private Map<String, String> describeVm(InstancesClient client, String zone, String vm) {

        Instance i = client.get(PROJECT_ID, zone, vm);
        Map<String,String> m = new HashMap<>();
        m.put("name",   i.getName());
        m.put("zone",   zone);
        m.put("status", i.getStatus());

        String privateIp = null, publicIp = null;
        if (!i.getNetworkInterfacesList().isEmpty()) {
            var ni = i.getNetworkInterfaces(0);
            privateIp = ni.getNetworkIP();
            if (!ni.getAccessConfigsList().isEmpty()) {
                publicIp = ni.getAccessConfigs(0).getNatIP();
            }
        }
        m.put("privateIP", privateIp);   // always present (maybe null)
        m.put("publicIP",  publicIp);    // always present (maybe null)
        return m;
    }

    /** @return true if the instance status is RUNNING */
    public boolean isVmRunning(String zone, String vmName) throws IOException {
        return "RUNNING".equalsIgnoreCase(getInstanceDetails(zone, vmName).getStatus());
    }


    /* --------------------------------------------------------------- */
    /* INTERNAL – single-instance fetch                                */
    /* --------------------------------------------------------------- */

    /**
     * Cheap one-liner using the Compute API.
     * Feel free to cache or batch if you call this very often.
     */
    private Instance getInstanceDetails(String zone, String vmName) throws IOException {
        try (InstancesClient client = InstancesClient.create()) {
            return client.get(PROJECT_ID, zone, vmName);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Cloudflare helpers                                                 */
    /* ------------------------------------------------------------------ */

    /** GET the DNS record id for vmName.nineforce.com (A record). */
    private String getInstanceRecordId(String vmName) {
        String dnsName = vmName + DOMAIN_SUFFIX;
        String api = CF_BASE + "/zones/" + CLOUDFLARE_ZONE_ID
                + "/dns_records?type=A&name=" + dnsName;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(api))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + CLOUDFLARE_API_TOKEN)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code / 100 != 2) {
                log.error("Cloudflare record lookup failed ({}): {}", code, resp.body());
                return null;
            }
            JsonNode json = MAPPER.readTree(resp.body());
            JsonNode result = json.path("result");
            if (result.isArray() && !result.isEmpty()) {
                return result.get(0).path("id").asText();
            } else {
                log.error("Failed to get DNS record ID for {}. No records found.", dnsName);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /** PUT the A record for vmName.nineforce.com to the given public IP. */
    private String updateCloudflareDNS(String vmName, String publicIP) {
        log.info("Updating Cloudflare DNS to IP: {}", publicIP);

        String recordId = getInstanceRecordId(vmName);
        if (recordId == null || recordId.isBlank()) {
            String msg = "DNS record id not found; skipping update.";
            log.error("Error: {}", msg);
            return msg;
        }

        String url = CF_BASE + "/zones/" + CLOUDFLARE_ZONE_ID + "/dns_records/" + recordId;
        String payload = "{\"type\":\"A\",\"name\":\"" + vmName + DOMAIN_SUFFIX + "\","
                + "\"content\":\"" + publicIP + "\",\"ttl\":120,\"proxied\":false}";

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + CLOUDFLARE_API_TOKEN)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            log.info("Cloudflare response ({}): {}", code, resp.body());
            if (code >= 200 && code < 300) {
                return "Success - Updated DNS record for " + vmName + DOMAIN_SUFFIX + " to IP: " + publicIP;
            }
            return "Cloudflare update failed (" + code + "): " + resp.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating Cloudflare DNS", e);
            return e.getMessage();
        }

    }


    /** GET the A-record content (IP) for name.nineforce.com. */
    @SuppressWarnings("SameParameterValue")
    private String getARecordContent(String name) {
        String dnsName = name + DOMAIN_SUFFIX;
        String api = CF_BASE + "/zones/" + CLOUDFLARE_ZONE_ID
                + "/dns_records?type=A&name=" + dnsName;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(api))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + CLOUDFLARE_API_TOKEN)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.error("Cloudflare content lookup failed ({}): {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode result = MAPPER.readTree(resp.body()).path("result");
            if (result.isArray() && !result.isEmpty()) {
                return result.get(0).path("content").asText(null);
            }
            log.warn("No A record content found for {}", dnsName);
            return null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            log.error("Error fetching A-record content for {}", dnsName, e);
            return null;
        }
    }

    // ---- error helpers ---------------------------------------------------
    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        String msg = c.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : c.toString();
   }
}
