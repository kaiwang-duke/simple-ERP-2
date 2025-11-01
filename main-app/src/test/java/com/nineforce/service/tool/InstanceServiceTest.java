package com.nineforce.service.tool;

import com.nineforce.model.ServiceResponse;
import static com.nineforce.service.tool.vmlease.GcpVmService.PROJECT_ID;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.compute.v1.AccessConfig;
import com.google.cloud.compute.v1.AggregatedListInstancesRequest;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesScopedList;
import com.google.cloud.compute.v1.NetworkInterface;
import com.google.cloud.compute.v1.Operation;
import com.google.cloud.compute.v1.ZoneOperationsClient;

import com.nineforce.util.LogUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for the *legacy* {@link InstanceService}.
 * We intercept:
 *  • GCP SDK – via Mockito’s MockedStatic to return our mocked InstancesClient
 *  • Cloudflare – via WireMock on a random port
 */
@ExtendWith(MockitoExtension.class)
//@ExtendWith(WireMockExtension.class)          // starts a WireMock server
class InstanceServiceTest {

    // ───────────────────────────────────────────────────────────────────────
    // collaborators we will fake
    // ───────────────────────────────────────────────────────────────────────
    @Mock
    private InstancesClient gcp;      // mocked SDK client
    @Mock
    private ZoneOperationsClient zoneOps;  // for long-running op
    @Mock
    private Operation gcpOp;    // fake GCP operation

    private InstanceService svc;                        // SUT (real)
    private WireMockServer wireMockServer;

    // Store static mocks to properly clean them up
    private MockedStatic<InstancesClient> mockedInstancesClient;
    private MockedStatic<ZoneOperationsClient> mockedZoneOpsClient;

    private static final Logger log = LoggerFactory.getLogger(InstanceServiceTest.class);

    @BeforeEach
    void init() {
        // start WireMock on a dynamic port
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        // make sure the test uses the stubbed Cloudflare base URL
        System.setProperty("cloudflare.base-url", wireMockServer.baseUrl());

        // ---- Cloudflare stubs ----
        String zoneId = "1b177330b2d32c95cfe70bbb17369e17"; // same as in code
        String recordName = "vm.nineforce.com";

        wireMockServer.stubFor(get(urlPathEqualTo("/zones/" + zoneId + "/dns_records"))
                .withQueryParam("type", equalTo("A"))
                .withQueryParam("name", equalTo(recordName))
                .willReturn(okJson("{\"success\":true,\"result\":[{\"id\":\"rec-123\"}]}")));

        wireMockServer.stubFor(put(urlPathEqualTo("/zones/" + zoneId + "/dns_records/rec-123"))
                .willReturn(okJson("{\"success\":true,\"result\":{\"id\":\"rec-123\"}}")));


        // replace static factory methods with mocks
        mockedInstancesClient = mockStatic(InstancesClient.class);
        mockedInstancesClient.when(InstancesClient::create).thenReturn(gcp);

        mockedZoneOpsClient = mockStatic(ZoneOperationsClient.class);
        mockedZoneOpsClient.when(ZoneOperationsClient::create).thenReturn(zoneOps);

        svc = new InstanceService(); // uses the mocked statics
    }

    @AfterEach
    void tearDown() {
        // Clear interrupt flag so Jetty can stop cleanly
        boolean wasInterrupted = Thread.currentThread().isInterrupted();
        if (wasInterrupted) {
            // clear the flag; reference the return value so analyzers don't warn
            if (Thread.interrupted()) { /*noop, cleared, no warning*/ }
        }


        try {
            if (wireMockServer != null && wireMockServer.isRunning()) {
                wireMockServer.stop();
            }
        } finally {
            if (mockedInstancesClient != null) mockedInstancesClient.close();
            if (mockedZoneOpsClient != null)   mockedZoneOpsClient.close();

            // restore flag if it was set
            if (wasInterrupted) Thread.currentThread().interrupt();
        }
    }


    /* ══════════════════════════════════════════════════════════════════════
       1.  listAllInstances()
       ════════════════════════════════════════════════════════════════════ */
    @Nested
    @DisplayName("listAllInstances()")
    class ListAll {

        /** happy path with a single zone / instance */
        @Test
        void returnsFlattenedList() throws IOException {

            // ── Prepare a fake GCP response ───────────────────────────────
            Instance vm = Instance.newBuilder()
                    .setName("dev")
                    .setStatus("RUNNING")
                    .addNetworkInterfaces(
                            NetworkInterface.newBuilder()
                                    .setNetworkIP("10.0.0.1")
                                    .addAccessConfigs(AccessConfig.newBuilder().setNatIP("34.1.2.3"))
                                    .build()
                    )
                    .build();
            InstancesScopedList scoped = InstancesScopedList.newBuilder()
                    .addInstances(vm)
                    .build();

            //AggregatedListInstancesRequest anyReq = AggregatedListInstancesRequest.getDefaultInstance();

            // paged response is an iterable map: “zones/us-central1-a → scopedList”
            InstancesClient.AggregatedListPagedResponse paged =
                    mock(InstancesClient.AggregatedListPagedResponse.class);
            when(paged.iterateAll()).thenReturn(
                    List.of(Map.entry("zones/us-central1-a", scoped))
            );

            when(gcp.aggregatedList(Mockito.<AggregatedListInstancesRequest>any())).thenReturn(paged);

            // ── act ───────────────────────────────────────────────────────
            List<Map<String,String>> list = svc.listAllInstances();

            // ── assert ────────────────────────────────────────────────────
            assertThat(list).singleElement().satisfies(m -> {
                assertThat(m).containsEntry("name",     "dev")
                        .containsEntry("status",   "RUNNING")
                        .containsEntry("zone",     "us-central1-a")
                        .containsEntry("publicIP", "34.1.2.3");
            });
        }

        /** propagates the ApiException transparently */
        @Test
        void throwsWhenGcpFails() {
            when(gcp.aggregatedList(Mockito.<AggregatedListInstancesRequest>any())).thenThrow(ApiException.class);
            assertThrows(ApiException.class, svc::listAllInstances);
        }
    }

    /* ══════════════════════════════════════════════════════════════════════
       2.  startInstance()
       ════════════════════════════════════════════════════════════════════ */
    @Nested @DisplayName("startInstance(zone, name)")
    class Start {

        @Test
        void happyPath_startsVm() throws Exception {
            // future returned by the async start call
            @SuppressWarnings("unchecked")
            OperationFuture<Operation, Operation> startFuture = mock(OperationFuture.class);
            when(gcp.startAsync(anyString(), anyString(), anyString())).thenReturn(startFuture);
            when(startFuture.get()).thenReturn(gcpOp);
            when(gcpOp.hasError()).thenReturn(false);

            // after start completes, service may read back the VM
            Instance started = Instance.newBuilder()
                    .setName("vm")
                    .setStatus("RUNNING")
                    .addNetworkInterfaces(
                            NetworkInterface.newBuilder()
                                    .setNetworkIP("10.0.0.5")
                                    .addAccessConfigs(AccessConfig.newBuilder().setNatIP("5.6.7.8"))
                                    .build()
                    )
                    .build();
            when(gcp.get(anyString(), anyString(), anyString())).thenReturn(started);

            // act
            ServiceResponse<?> res = svc.startInstance("us", "vm");

            // assert
            assertEquals("success", res.getStatus());
            verify(gcp).startAsync(PROJECT_ID, "us", "vm");
            verify(gcp).get(PROJECT_ID, "us", "vm");
        }


        @Test void gcpThrows_returnsError() throws InterruptedException {
            @SuppressWarnings("unchecked")
            OperationFuture<Operation, Operation> startFuture = mock(OperationFuture.class);
            when(gcp.startAsync(anyString(), anyString(), anyString())).thenReturn(startFuture);
            try {
                when(startFuture.get()).thenThrow(new ExecutionException(new IOException("Unit Test - Expected Exception")));
            } catch (ExecutionException e) {
                // won't happen during stubbing
                throw new AssertionError(e);
            }

            ServiceResponse<?> res = svc.startInstance("z","vm");

            // make the log block super obvious in CI output:
            LogUtil.exceptionBlock(
                    log,
                    "EXPECTED ERROR PATH (wrapped by service)",
                    new RuntimeException(res.getError()),
                    "zone","z","vm","vm","status",res.getStatus()
            );

            assertEquals("error", res.getStatus());
            assertThat(res.getError()).contains("Unit Test - Expected Exception");
        }
    }

    /* ══════════════════════════════════════════════════════════════════════
       3.  stopInstance()
       ════════════════════════════════════════════════════════════════════ */
    @Nested @DisplayName("stopInstance(zone, name)")
    class Stop {

        @Test void happyPath_stopsVm() throws Exception {

            @SuppressWarnings("unchecked")
            OperationFuture<Operation, Operation> stopFuture = mock(OperationFuture.class);
            when(stopFuture.get()).thenReturn(gcpOp);
            when(gcp.stopAsync(anyString(), anyString(), anyString())).thenReturn(stopFuture);
            when(gcpOp.hasError()).thenReturn(false);

            Instance stopped = Instance.newBuilder()
                    .setName("vm")
                    .setStatus("TERMINATED")
                    .addNetworkInterfaces( NetworkInterface.newBuilder().setNetworkIP("10.0.0.5")
                    .addAccessConfigs(AccessConfig.newBuilder().setNatIP("5.6.7.8")).build() )
                    .build();
            when(gcp.get(anyString(), anyString(), anyString())).thenReturn(stopped);

            Map<String,String> details = svc.stopInstance("us","vm");

            assertEquals("TERMINATED", details.get("status"));
            verify(gcp).stopAsync(PROJECT_ID, "us", "vm");
        }
    }
}
