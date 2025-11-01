package com.nineforce.service.tool;

import com.nineforce.service.tool.vmlease.GcpVmService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;

import com.nineforce.model.ServiceResponse;


@Service
public class InstanceService {

    private static final Logger logger = LoggerFactory.getLogger(InstanceService.class);

    // delegate for VM lifecycle operations
    private final GcpVmService gcpVm;

    public InstanceService() {
        this.gcpVm = new GcpVmService(); // thin layer: delegate to service
        if (isRunningOnGCP()) {
            try {
                String serviceAccountEmail = getServiceAccountEmail();
                logger.info("Service Account Email: {}", serviceAccountEmail);
            } catch (IOException e) {
                logger.error("Error fetching service account email", e);
            }
        } else {
            logger.info("Not running on GCP, skipping service account email fetch.");
        }
    }

    // convenience ctor for tests (dependency injection)
    public InstanceService(GcpVmService gcpVmService) {
                this.gcpVm = gcpVmService;
    }

    private boolean isRunningOnGCP() {
        try {
            InetAddress addr = InetAddress.getByName("metadata.google.internal");
            return addr != null && !addr.getHostAddress().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private String getServiceAccountEmail() throws IOException {
        String metadataUrl = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email";
        URL url = new URL(metadataUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Metadata-Flavor", "Google");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return reader.readLine();
        }
    }

    public List<Map<String, String>> listAllInstances() throws IOException {
        return gcpVm.listAllInstances();
    }

    // in InstanceService
    public ServiceResponse<Map<String, String>> startInstance(String zone, String instanceName) {
        logger.info("Starting instance: {} in zone: {}", instanceName, zone);
        try {
            Map<String,String> details = gcpVm.startVm(zone, instanceName); // DNS already done here
            return new ServiceResponse<>("success", details, null);
        } catch (IOException e) {
            logger.error("Error starting instance", e);
            return new ServiceResponse<>("error", null, e.getMessage());
        }
    }

    public Map<String, String> stopInstance(String zone, String instanceName) throws IOException {
        logger.info("Stopping instance: {} in zone: {}", instanceName, zone);
        return gcpVm.stopVm(zone, instanceName);
    }

    public void stopAllInstances() throws IOException {
        logger.info("Stopping all instances");
        gcpVm.stopAllVm();
    }


    public String setProxy(String publicIP) {
        return gcpVm.setProxyIp(publicIP);
    }

    /**
     * Get the current proxy IP address from Cloudflare DNS
     * @return the current proxy IP address
     */
    public String getCurrentProxyIP() {
        return gcpVm.getProxyIp(); // ensure proxy is set
    }
}
