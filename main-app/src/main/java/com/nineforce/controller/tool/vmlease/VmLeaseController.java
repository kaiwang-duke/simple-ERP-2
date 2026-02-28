package com.nineforce.controller.tool.vmlease;   // keep the same top‑level “tool”
// (rename if you finally settle on plural)

import com.nineforce.model.tool.vmlease.VmLease;
import com.nineforce.service.tool.InstanceService;   // still used only for “list”
import com.nineforce.service.tool.vmlease.VmLeaseService;
import com.nineforce.util.FirebaseAuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.time.Duration;

/**
 * Thin MVC layer around {@link VmLeaseService}.<br/>
 * <ul>
 *   <li><code>GET /tools/vmleases</code> – dashboard (instances + current leases)</li>
 *   <li><code>POST /tools/vmleases/start</code>   – start or first‑time lease&nbsp;(15 min)</li>
 *   <li><code>POST /tools/vmleases/extend</code> – extend existing lease (+15 min)</li>
 *   <li><code>POST /tools/vmleases/stop</code>   – manual shutdown & lease delete</li>
 *   <li><code>POST /tools/vmleases/stopAll</code> – one‑click emergency stop</li>
 *   <li><code>POST /tools/vmleases/setProxy</code> – unchanged helper for Cloudflare</li>
 * </ul>
 *
 * Views:
 *  • <code>tools/vmleases/list</code> (dashboard)
 *  • <code>redirect:/tools/vmleases</code> for all POSTs, with flash‑msgs
 */
@Slf4j
@Controller
@RequestMapping("/tools/vmleases")
@RequiredArgsConstructor
public class VmLeaseController {
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final VmLeaseService   leaseSvc;
    private final InstanceService  instanceSvc;     // only for listing / proxy
    private final FirebaseAuthUtil auth;

    /* ------------------------------------------------------------------ */
    /* DASHBOARD                                                          */
    /* ------------------------------------------------------------------ */

    @GetMapping
    public String dashboard(Model model) throws IOException {

        // 1) live info from GCE
        List<Map<String, String>> rawInstances = instanceSvc.listAllInstances();

        // 2) leases in DB – keyed by vmName
        Map<String, VmLease> leases = leaseSvc.currentLeases()
                .stream()
                .collect(Collectors.toMap(VmLease::getVmName, l -> l));

        OffsetDateTime now = OffsetDateTime.now();

        // 3) Build clean view‑objects
        List<VmRow> instances = rawInstances.stream().map(m -> {
            VmLease lease = leases.get(m.get("name"));

            OffsetDateTime started = (lease != null) ? lease.getStartedAt() : null;
            OffsetDateTime expires = (lease != null) ? lease.getExpiresAt() : null;
            String user = (lease != null) ? lease.getUserEmail() : null;
            long remaining = (lease != null)
                    ? Duration.between(now, expires).toMinutes()
                    : 0;

            return new VmRow(
                    m.get("name"),
                    m.get("zone"),
                    m.get("status"),
                    m.get("publicIP"),
                    user,
                    started,
                    expires,
                    remaining
            );
        })
        .sorted(Comparator.comparing(VmRow::name, String.CASE_INSENSITIVE_ORDER))
        .toList();

        model.addAttribute("instances", instances);
        model.addAttribute("currentProxyIP", instanceSvc.getCurrentProxyIP());
        //model.addAttribute("userEmail", auth.getUserEmail());
        model.addAttribute("title", "Google VM Dashboard 0.3");
        return "tools/vmleases/list";
    }


    /* ------------------------------------------------------------------ */
    /* ACTIONS – start / extend / stop                                    */
    /* ------------------------------------------------------------------ */

    @PostMapping("/start")
    public String start(@RequestParam String zone,
                        @RequestParam String vmName,
                        RedirectAttributes ra) {

        try {
            VmLease lease = leaseSvc.startVm(zone, vmName, auth.getUserEmail());
            ra.addFlashAttribute("msg", "VM “" + vmName + "” started until "
                    + formatBeijingTime(lease.getExpiresAt()) + " Beijing.");
        } catch (Exception ex) {
            log.error("Failed to start VM {}", vmName, ex);
            ra.addFlashAttribute("err", "Error starting VM: " + ex.getMessage());
        }
        return "redirect:/tools/vmleases";
    }

    /** Adds +15 min to an existing lease (or starts if none exists). */
    @PostMapping("/extend")
    public String extend(@RequestParam String zone,
                         @RequestParam String vmName,
                         RedirectAttributes ra) {

        try {
            VmLease lease = leaseSvc.extendLease(zone, vmName, auth.getUserEmail());
            ra.addFlashAttribute("msg", "VM “" + vmName + "” extended to "
                    + formatBeijingTime(lease.getExpiresAt()) + " Beijing.");
        } catch (Exception ex) {
            log.error("Failed to extend VM {}", vmName, ex);
            ra.addFlashAttribute("err", "Error extending VM: " + ex.getMessage());
        }
        return "redirect:/tools/vmleases";
    }

    @PostMapping("/stop")
    public String stop(@RequestParam String zone,
                       @RequestParam String vmName,
                       RedirectAttributes ra) {

        try {
            leaseSvc.stopAndRelease(zone, vmName, auth.getUserEmail());   // see note ↓
            ra.addFlashAttribute("msg", "VM “" + vmName + "” stopped.");
        } catch (Exception ex) {
            log.error("Failed to stop VM {}", vmName, ex);
            ra.addFlashAttribute("err", "Error stopping VM: " + ex.getMessage());
        }
        return "redirect:/tools/vmleases";
    }

    /* ------------------------------------------------------------------ */
    /* BULK & MISC                                                        */
    /* ------------------------------------------------------------------ */

    @PostMapping("/stopAll")
    public String stopAll(RedirectAttributes ra) {
        leaseSvc.stopAll();                       // optional helper
        ra.addFlashAttribute("msg", "All VMs stopped.");
        return "redirect:/tools/vmleases";
    }

    @PostMapping("/setProxy")
    public String setProxy(@RequestParam String publicIP,
                           RedirectAttributes ra) {
        String res = instanceSvc.setProxy(publicIP);
        ra.addFlashAttribute("msg", res);
        return "redirect:/tools/vmleases";
    }

    private static String formatBeijingTime(OffsetDateTime ts) {
        return ts.atZoneSameInstant(BEIJING_ZONE).format(TIME_FMT);
    }
}
