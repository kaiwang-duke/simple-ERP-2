package com.nineforce.controller.tool;

import com.nineforce.util.FirebaseAuthUtil;

import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.nineforce.service.tool.CloudSqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CloudSqlController {

    @Autowired
    private CloudSqlService cloudSqlService;

    @Autowired
    private FirebaseAuthUtil firebaseAuthUtil;

    private static final Logger logger = LoggerFactory.getLogger(CloudSqlController.class);

    @GetMapping("/tools/cloud-sql/cloud-sql-manager")
    public String manageCloudSql(Model model) {
        model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        model.addAttribute("title", "SQL Instance Manager");

        String instanceId = "ecom"; // Example instance ID
        try {
            // Get the instance status
            DatabaseInstance instance = cloudSqlService.getInstanceStatus(instanceId);
            String status = instance.getState(); // Get the instance state (e.g., RUNNABLE, STOPPED)
            String activationPolicy = instance.getSettings().getActivationPolicy();
            logger.debug("Instance activation policy: " + activationPolicy);
            logger.info("Instance status: " + status);

            // Add the status to the model
            model.addAttribute("instanceStatus", status);
            model.addAttribute("activationPolicy", activationPolicy);
            model.addAttribute("instanceId", instanceId);
        } catch (Exception e) {
            model.addAttribute("instanceStatus", "Error retrieving instance status");
            model.addAttribute("errorMessage", e.getMessage());
        }

        // Return the name of the view to be rendered
        return "tools/cloud-sql/cloud-sql-manager";  // This will resolve to a template named "cloud-sql-manager.html"
    }


    // New API endpoint to start the instance
    @PostMapping("/api/start-instance")
    public ResponseEntity<String> startInstance(@RequestParam String instanceId) {
        try {
            cloudSqlService.startInstance(instanceId); // Call the service to start the instance
            logger.info("Instance " + instanceId + " started successfully.");
            return ResponseEntity.ok("Instance started successfully.");
        } catch (Exception e) {
            logger.error("Failed to start instance: " + e.getMessage());
            return ResponseEntity.status(500).body("Failed to start instance: " + e.getMessage());
        }
    }
}
