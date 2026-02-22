package com.nineforce.controller.tool;

import com.nineforce.repository.tool.vmlease.VmLeaseRepository;
import com.nineforce.service.tool.InstanceService;
import com.nineforce.util.FirebaseAuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.nineforce.model.ServiceResponse;

@Controller
@RequestMapping("/tools/instance")
public class InstanceController {
    @Autowired
    private InstanceService instanceService;
    @Autowired
    private FirebaseAuthUtil firebaseAuthUtil;
    @Autowired
    private VmLeaseRepository vmLeaseRepository;

    private static final Logger logger = LoggerFactory.getLogger(InstanceController.class);

    @GetMapping
    public String listAllInstances(Model model) throws IOException {
        List<Map<String, String>> instances = instanceService.listAllInstances();
        String currentProxyIP = instanceService.getCurrentProxyIP();
        logger.info("currentProxyIP: " + currentProxyIP);

        model.addAttribute("instances", instances);
        model.addAttribute("currentProxyIP", currentProxyIP);
        model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        model.addAttribute("title", "Google VM with Proxy");
        return "tools/instance/list-instance";
    }


    @PostMapping("/start")
    public String startInstance(@RequestParam String zone, @RequestParam String instanceName, Model model) {
        ServiceResponse<Map<String, String>> response = instanceService.startInstance(zone, instanceName);

        if ("success".equals(response.getStatus())) {
            model.addAttribute("status", "success");
            model.addAttribute("message", "Instance started successfully.");
            model.addAttribute("instanceDetails", response.getResult());
        } else {
            model.addAttribute("status", "error");
            model.addAttribute("message", "Error starting instance: " + response.getError());
        }

        model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        model.addAttribute("title", "Google VM");

        return "tools/instance/instanceStartResult";
    }

    @PostMapping("/stop")
    public String stopInstance(@RequestParam String zone, @RequestParam String instanceName, Model model) {
        try {
            Map<String, String> instanceDetails = instanceService.stopInstance(zone, instanceName);
            vmLeaseRepository.deleteById(instanceName); // keep vm_lease table in sync for manual stops

            model.addAttribute("status", "success");
            model.addAttribute("message", "Instance stopped successfully.");
            model.addAttribute("instanceDetails", instanceDetails);
            model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
            model.addAttribute("title", "Google VM");
        } catch (IOException e) {
            model.addAttribute("status", "error");
            model.addAttribute("message", "Error stopping instance: " + e.getMessage());
        }
        return "tools/instance/instanceStopResult";
    }

    @PostMapping("/stopAll")
    public String stopAllInstances(Model model) throws IOException {
        instanceService.stopAllInstances();
        vmLeaseRepository.deleteAll(); // clear tracked leases after bulk stop from legacy page
        return "redirect:/tools/instance";
    }

    @PostMapping("/setProxy")
    public String setProxy(@RequestParam String instanceName, @RequestParam String publicIP, Model model) {
        String result = instanceService.setProxy(publicIP);

        if ("success".equals(result)) {
            model.addAttribute("status", "success");
            model.addAttribute("message", "Proxy set successfully.");
        } else {
            model.addAttribute("status", "error");
            model.addAttribute("message", "Error setting proxy: " + result);
        }
        return "redirect:/tools/instance";
    }

}
