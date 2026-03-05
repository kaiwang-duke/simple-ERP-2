package com.nineforce.service.tool.vmlease;

import com.nineforce.model.tool.vmlease.VmLease;
import com.nineforce.repository.tool.vmlease.VmLeaseRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VmLeaseService {

    private static final Duration  INITIAL  = Duration.ofMinutes(30);
    private static final Duration  EXTEND   = Duration.ofMinutes(15);
    private static final Duration  QUARTER  = Duration.ofMinutes(15);

    private final VmLeaseRepository repo;
    private final GcpVmService      gcp;

    /* ------------------------------------------------------------------ */
    /* PUBLIC                                                             */
    /* ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------ */
    /* START – called when user presses the “Start” button                */
    /* ------------------------------------------------------------------ */
    @Transactional
    public VmLease startVm(String zone, String vmName, String userEmail) throws Exception {

        VmLease lease = repo.findById(vmName).orElse(null);
        OffsetDateTime now = currentTime();
        OffsetDateTime rounded = roundUp(now, QUARTER);

        // ── 1) If VM already running just throw -> UI will offer “Extend”
        if (lease != null && gcp.isVmRunning(zone, vmName)) {
            throw new IllegalStateException("VM already running – use Extend");
        }

        // ── 2) Start (or restart) the machine
        gcp.startVm(zone, vmName);
        OffsetDateTime exp = rounded.plus(INITIAL);

        if (lease == null) {                               // first ever run
            lease = new VmLease(vmName, zone, userEmail, now, exp);
        } else {                                           // row existed but VM was off
            lease.setStartedAt(now);
            lease.setExpiresAt(exp);
            lease.setUserEmail(userEmail);
        }
        repo.save(lease);

        log("START_VM", userEmail, vmName,
                Map.of("expires_at", exp.toString()));
        return lease;
    }

    /* ------------------------------------------------------------------ */
    /* EXTEND – adds +15 min to an *existing & running* VM                */
    /* ------------------------------------------------------------------ */
    @Transactional
    public VmLease extendLease(String zone, String vmName, String userEmail) throws Exception {

        VmLease lease = repo.findById(vmName)
                .orElseThrow(() -> new IllegalStateException("No lease found – press Start"));
        if (!gcp.isVmRunning(zone, vmName)) {
            throw new IllegalStateException("VM is stopped – press Start");
        }

        OffsetDateTime now = currentTime();
        OffsetDateTime newExp = lease.getExpiresAt().plus(EXTEND);

        // guarantee at least +15 min from *now*
        if (newExp.isBefore(now.plus(EXTEND))) newExp = now.plus(EXTEND);

        lease.setExpiresAt(roundUp(newExp, QUARTER));
        lease.setUserEmail(userEmail);
        repo.save(lease);

        log("EXTEND_VM", userEmail, vmName,
                Map.of("expires_at", lease.getExpiresAt().toString()));
        return lease;
    }

    /** Current leases for the UI. */
    public List<VmLease> currentLeases() { return repo.findAll(); }

    /* ----------------------------------------------------------------------- */
    /* SCHEDULER – auto‑stop expired VMs every 15 minutes (+2 min grace)       */
    /* @Scheduled(fixedDelay = 60_000)   // test-only alternative               */
    /* ----------------------------------------------------------------------- */

    @Scheduled(cron = "0 2/15 * * * *", zone = "UTC")  // :02, :17, :32, :47 (2-min grace after quarter)
    @Transactional
    public void autoShutdown() {
        OffsetDateTime now = currentTime();
        List<VmLease> due = repo.findExpired(now);

        for (VmLease l : due) {
            try {
                gcp.stopVm(l.getZone(), l.getVmName());
                repo.delete(l);
                log("AUTO_STOP_VM", "system", l.getVmName(), Map.of());
            } catch (Exception ex) {
                log.error("Failed to auto‑stop {}", l.getVmName(), ex);
            }
        }
    }

    /**
     * Manual “Stop” button – shuts the VM down immediately
     * **and** removes the lease so the row disappears from the dashboard.
     */
    @Transactional
    public void stopAndRelease(String zone, String vmName, String userEmail) throws Exception {
        gcp.stopVm(zone, vmName);
        repo.deleteById(vmName);                 // id = vm_name PK
        log("STOP_VM", userEmail, vmName, Map.of());
    }

    /**
     * “Stop All” – shuts down **all** running VMs that still have a lease
     * and then wipes the vm_lease table so the dashboard is empty.
     */
    @Transactional
    public void stopAll() {
        List<VmLease> leases = repo.findAll();           // snapshot first
        for (VmLease l : leases) {
            try {
                gcp.stopVm(l.getZone(), l.getVmName());
                log("STOP_VM", "system", l.getVmName(), Map.of());
            } catch (Exception ex) {
                log.error("Failed to stop {}", l.getVmName(), ex);
                // decide whether to keep the row so the scheduler can retry
            }
        }
        repo.deleteAll();                                // clear table last
        log.info("All leases cleared by system.");
    }

    /* ------------------------------------------------------------------ */
    /* helpers                                                            */
    /* ------------------------------------------------------------------ */

    private OffsetDateTime roundUp(OffsetDateTime ts, Duration bucket) {
        if (bucket.isZero() || bucket.isNegative()) {
            throw new IllegalArgumentException("bucket must be positive");
        }

        Instant instant = ts.toInstant();
        long bucketNanos = bucket.toNanos();
        long epochNanos = Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
                instant.getNano()
        );
        long roundedNanos = Math.multiplyExact(
                Math.floorDiv(Math.addExact(epochNanos, bucketNanos - 1), bucketNanos),
                bucketNanos
        );

        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(
                        Math.floorDiv(roundedNanos, 1_000_000_000L),
                        Math.floorMod(roundedNanos, 1_000_000_000L)
                ),
                ts.getOffset()
        );
    }

    // Isolated for deterministic unit tests without introducing a Clock bean.
    OffsetDateTime currentTime() {
        return OffsetDateTime.now();
    }

    private void log(String action, String user, String vm, Map<String,String> d)  {
        // TODO: insert into activity_log when you add that table
        log.info("{} – user={} vm={} details={}", action, user, vm, d);
    }
}
