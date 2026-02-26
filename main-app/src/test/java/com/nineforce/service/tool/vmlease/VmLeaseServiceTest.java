package com.nineforce.service.tool.vmlease;

import com.nineforce.model.tool.vmlease.VmLease;
import com.nineforce.repository.tool.vmlease.VmLeaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VmLeaseServiceTest {

    private FakeRepo fakeRepo;
    private FakeGcpVmService fakeGcp;
    private TestableVmLeaseService svc;
    private List<String> events;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        fakeRepo = new FakeRepo(events);
        fakeGcp = new FakeGcpVmService(events);
        svc = new TestableVmLeaseService(fakeRepo.repository(), fakeGcp);
    }

    @Nested
    @DisplayName("startVm")
    class StartVm {

        @Test
        void createsLeaseForFirstRun_andRoundsExpiryToQuarterHour() throws Exception {
            svc.setNow(OffsetDateTime.of(2026, 2, 24, 10, 7, 10, 0, ZoneOffset.UTC));

            VmLease lease = svc.startVm("us-central1-a", "vm1", "user@nineforce.com");

            assertThat(lease.getVmName()).isEqualTo("vm1");
            assertThat(lease.getZone()).isEqualTo("us-central1-a");
            assertThat(lease.getUserEmail()).isEqualTo("user@nineforce.com");
            assertThat(lease.getStartedAt()).isEqualTo(svc.currentTime());
            assertThat(lease.getExpiresAt()).isEqualTo(
                    OffsetDateTime.of(2026, 2, 24, 10, 45, 0, 0, ZoneOffset.UTC)
            );
            assertThat(fakeRepo.lastSaved).isSameAs(lease);
            assertThat(fakeGcp.startCalls).containsExactly("us-central1-a/vm1");
        }

        @Test
        void throwsWhenLeaseExistsAndVmAlreadyRunning() {
            fakeRepo.put(lease("vm1", "us-central1-a", "old@nineforce.com",
                    ts(2026, 2, 24, 9, 0), ts(2026, 2, 24, 9, 30)));
            fakeGcp.running.add("us-central1-a/vm1");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> svc.startVm("us-central1-a", "vm1", "user@nineforce.com"));

            assertThat(ex).hasMessageContaining("already running");
            assertThat(fakeGcp.startCalls).isEmpty();
            assertThat(fakeRepo.lastSaved).isNull();
        }

        @Test
        void reusesExistingRowWhenVmWasStopped() throws Exception {
            VmLease existing = lease("vm1", "us-central1-a", "old@nineforce.com",
                    ts(2026, 2, 24, 9, 0), ts(2026, 2, 24, 9, 30));
            fakeRepo.put(existing);
            svc.setNow(ts(2026, 2, 24, 10, 58));

            VmLease lease = svc.startVm("us-central1-a", "vm1", "new@nineforce.com");

            assertThat(lease).isSameAs(existing);
            assertThat(lease.getStartedAt()).isEqualTo(ts(2026, 2, 24, 10, 58));
            assertThat(lease.getExpiresAt()).isEqualTo(ts(2026, 2, 24, 11, 30));
            assertThat(lease.getUserEmail()).isEqualTo("new@nineforce.com");
        }
    }

    @Nested
    @DisplayName("extendLease")
    class ExtendLease {

        @Test
        void throwsWhenLeaseMissing() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> svc.extendLease("us-central1-a", "vm1", "user@nineforce.com"));

            assertThat(ex).hasMessageContaining("No lease found");
            assertThat(fakeRepo.lastSaved).isNull();
        }

        @Test
        void throwsWhenVmIsNotRunning() {
            fakeRepo.put(lease("vm1", "us-central1-a", "u@x",
                    ts(2026, 2, 24, 10, 0), ts(2026, 2, 24, 10, 30)));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> svc.extendLease("us-central1-a", "vm1", "user@nineforce.com"));

            assertThat(ex).hasMessageContaining("stopped");
            assertThat(fakeRepo.lastSaved).isNull();
        }

        @Test
        void ensuresAtLeastFifteenMinutesFromNow_thenRoundsToQuarterHour() throws Exception {
            OffsetDateTime now = ts(2026, 2, 24, 10, 11);
            VmLease existing = lease("vm1", "us-central1-a", "old@nineforce.com",
                    now.minusMinutes(30), now.minusMinutes(6));
            fakeRepo.put(existing);
            fakeGcp.running.add("us-central1-a/vm1");
            svc.setNow(now);

            VmLease lease = svc.extendLease("us-central1-a", "vm1", "new@nineforce.com");

            assertThat(lease.getUserEmail()).isEqualTo("new@nineforce.com");
            assertThat(lease.getExpiresAt()).isEqualTo(ts(2026, 2, 24, 10, 30));
            assertThat(fakeRepo.lastSaved).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("autoShutdown")
    class AutoShutdown {

        @Test
        void stopsAndDeletesExpiredLeases_andContinuesAfterFailures() {
            OffsetDateTime now = ts(2026, 2, 24, 10, 17);
            fakeRepo.put(lease("vm-ok", "us-a", "u1", now.minusHours(1), now.minusMinutes(2)));
            fakeRepo.put(lease("vm-fail", "us-b", "u2", now.minusHours(1), now.minusMinutes(1)));
            fakeRepo.put(lease("vm-future", "us-c", "u3", now.minusHours(1), now.plusMinutes(5)));
            fakeGcp.failStops.add("us-b/vm-fail");
            svc.setNow(now);

            svc.autoShutdown();

            assertThat(fakeGcp.stopCalls).containsExactly("us-a/vm-ok", "us-b/vm-fail");
            assertThat(fakeRepo.contains("vm-ok")).isFalse();
            assertThat(fakeRepo.contains("vm-fail")).isTrue();
            assertThat(fakeRepo.contains("vm-future")).isTrue();
        }
    }

    @Nested
    @DisplayName("manual/bulk stop")
    class StopFlows {

        @Test
        void stopAndReleaseStopsVmBeforeDeletingLeaseRow() throws Exception {
            svc.stopAndRelease("us-central1-a", "vm1", "user@nineforce.com");

            assertThat(events).containsSubsequence(
                    "gcp.stop:us-central1-a/vm1",
                    "repo.deleteById:vm1"
            );
        }

        @Test
        void stopAllAttemptsEachStop_thenClearsTable() {
            fakeRepo.put(lease("vm1", "us-a", "u1", ts(2026, 2, 24, 10, 0), ts(2026, 2, 24, 10, 30)));
            fakeRepo.put(lease("vm2", "us-b", "u2", ts(2026, 2, 24, 10, 0), ts(2026, 2, 24, 10, 30)));
            fakeGcp.failStops.add("us-a/vm1");

            svc.stopAll();

            assertThat(fakeGcp.stopCalls).containsExactlyInAnyOrder("us-a/vm1", "us-b/vm2");
            assertThat(events.get(events.size() - 1)).isEqualTo("repo.deleteAll");
            assertThat(fakeRepo.findAll()).isEmpty();
        }
    }

    @Test
    void currentLeasesDelegatesToRepository() {
        VmLease vm1 = lease("vm1", "z", "u", ts(2026, 2, 24, 10, 0), ts(2026, 2, 24, 10, 15));
        VmLease vm2 = lease("vm2", "z", "u", ts(2026, 2, 24, 10, 0), ts(2026, 2, 24, 10, 30));
        fakeRepo.put(vm1);
        fakeRepo.put(vm2);

        assertThat(svc.currentLeases()).containsExactly(vm1, vm2);
    }

    @Test
    void startVmPersistsUpdatedLeaseObject() throws Exception {
        OffsetDateTime now = ts(2026, 2, 24, 11, 14);
        VmLease existing = lease("vm1", "us-central1-a", "old@nineforce.com",
                now.minusHours(1), now.minusMinutes(20));
        fakeRepo.put(existing);
        svc.setNow(now);

        svc.startVm("us-central1-a", "vm1", "user@nineforce.com");

        assertThat(fakeRepo.lastSaved).isSameAs(existing);
    }

    private static OffsetDateTime ts(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC);
    }

    private static VmLease lease(String vmName, String zone, String user, OffsetDateTime startedAt, OffsetDateTime expiresAt) {
        return VmLease.builder()
                .vmName(vmName)
                .zone(zone)
                .userEmail(user)
                .startedAt(startedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private static final class TestableVmLeaseService extends VmLeaseService {
        private OffsetDateTime fixedNow;

        TestableVmLeaseService(VmLeaseRepository repo, GcpVmService gcp) {
            super(repo, gcp);
        }
 
        void setNow(OffsetDateTime now) {
            this.fixedNow = now;
        }

        @Override
        OffsetDateTime currentTime() {
            return fixedNow != null ? fixedNow : super.currentTime();
        }
    }

    private static final class FakeGcpVmService extends GcpVmService {
        private final List<String> events;
        private final Set<String> running = new HashSet<>();
        private final Set<String> failStops = new HashSet<>();
        private final List<String> startCalls = new ArrayList<>();
        private final List<String> stopCalls = new ArrayList<>();

        FakeGcpVmService(List<String> events) {
            this.events = events;
        }

        @Override
        public Map<String, String> startVm(String zone, String vmName) {
            String key = key(zone, vmName);
            startCalls.add(key);
            running.add(key);
            events.add("gcp.start:" + key);
            return Map.of("status", "RUNNING");
        }

        @Override
        public Map<String, String> stopVm(String zone, String vmName) throws IOException {
            String key = key(zone, vmName);
            stopCalls.add(key);
            events.add("gcp.stop:" + key);
            if (failStops.contains(key)) {
                throw new IOException("expected stop failure for " + key);
            }
            running.remove(key);
            return Map.of("status", "TERMINATED");
        }

        @Override
        public boolean isVmRunning(String zone, String vmName) {
            return running.contains(key(zone, vmName));
        }

        private static String key(String zone, String vmName) {
            return zone + "/" + vmName;
        }
    }

    private static final class FakeRepo implements InvocationHandler {
        private final Map<String, VmLease> store = new LinkedHashMap<>();
        private final List<String> events;
        private VmLeaseRepository repository;
        private VmLease lastSaved;

        FakeRepo(List<String> events) {
            this.events = events;
        }

        VmLeaseRepository repository() {
            if (repository == null) {
                repository = (VmLeaseRepository) Proxy.newProxyInstance(
                        VmLeaseRepository.class.getClassLoader(),
                        new Class<?>[]{VmLeaseRepository.class},
                        this
                );
            }
            return repository;
        }

        void put(VmLease lease) {
            store.put(lease.getVmName(), lease);
        }

        boolean contains(String vmName) {
            return store.containsKey(vmName);
        }

        List<VmLease> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            String name = method.getName();

            if (name.equals("toString")) return "FakeVmLeaseRepository";
            if (name.equals("hashCode")) return System.identityHashCode(this);
            if (name.equals("equals")) return proxy == args[0];

            switch (name) {
                case "findById" -> {
                    String id = (String) args[0];
                    return Optional.ofNullable(store.get(id));
                }
                case "save" -> {
                    VmLease lease = (VmLease) args[0];
                    lastSaved = lease;
                    store.put(lease.getVmName(), lease);
                    events.add("repo.save:" + lease.getVmName());
                    return lease;
                }
                case "findAll" -> {
                    return new ArrayList<>(store.values());
                }
                case "findExpired" -> {
                    OffsetDateTime now = (OffsetDateTime) args[0];
                    return store.values().stream()
                            .filter(l -> !l.getExpiresAt().isAfter(now))
                            .toList();
                }
                case "delete" -> {
                    VmLease lease = (VmLease) args[0];
                    store.remove(lease.getVmName());
                    events.add("repo.delete:" + lease.getVmName());
                    return null;
                }
                case "deleteById" -> {
                    String id = (String) args[0];
                    store.remove(id);
                    events.add("repo.deleteById:" + id);
                    return null;
                }
                case "deleteAll" -> {
                    store.clear();
                    events.add("repo.deleteAll");
                    return null;
                }
                default -> throw new UnsupportedOperationException("Method not implemented in FakeRepo: " + name);
            }
        }
    }
}
