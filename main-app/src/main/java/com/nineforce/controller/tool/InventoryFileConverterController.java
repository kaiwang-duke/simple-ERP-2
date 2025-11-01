package com.nineforce.controller.tool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;


import com.nineforce.util.FirebaseAuthUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nineforce.service.tool.InventoryFileConverterService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;


@Controller
@RequestMapping("/tools/inventory-file-converter")
public class InventoryFileConverterController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryFileConverterController.class);

    @Autowired
    private InventoryFileConverterService inventoryFileConverterService;
    @Autowired
    private FirebaseAuthUtil firebaseAuthUtil;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        model.addAttribute("title", "Inventory File Converter");
        return "tools/inventory-file-converter/upload";
    }

    @PostMapping("/process")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            String outStrFile = inventoryFileConverterService.createOrUpdateSkuLocSheet(file);
            redirectAttributes.addFlashAttribute("message", "File processed successfully: " + outStrFile);
            redirectAttributes.addFlashAttribute("downloadLink",
                    //"/tool/inventory-file-converter/download?filename=" + outStrFile);
                    outStrFile); // Ensure the correct path
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Failed to process file: " + e.getMessage());
        }
        return "redirect:/tools/inventory-file-converter";
    }

    /**
    @GetMapping("/success")
    public String success() {
        return "success";
    }
     */

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("filename") String filename) throws IOException {
        logger.info("Downloading file: " + filename);
        // Log the files in the /tmp directory for debugging
        File tmpDir = new File("/tmp");
        File[] files = tmpDir.listFiles();
        if (files != null) {
            for (File file : files) {
                logger.info("File in /tmp: " + file.getName());
            }
        }

        Path filePath = Paths.get(filename);
        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
