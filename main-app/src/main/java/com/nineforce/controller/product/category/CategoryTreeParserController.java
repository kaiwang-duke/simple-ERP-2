package com.nineforce.controller.product.category;

import com.nineforce.exception.MissingSheetException;
import com.nineforce.model.product.category.Category;
import com.nineforce.service.product.category.CategoryTreeParserService;
import com.nineforce.util.FirebaseAuthUtil;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Arrays;


@Controller
@RequestMapping("/products/categories/category-tree-parser")
public class CategoryTreeParserController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryTreeParserController.class);
    private final CategoryTreeParserService categoryTreeParserService;
    //private final FirebaseAuthUtil firebaseAuthUtil;

    public CategoryTreeParserController(CategoryTreeParserService categoryTreeParserService) {
        this.categoryTreeParserService = categoryTreeParserService;
        //this.firebaseAuthUtil = firebaseAuthUtil;
    }

    /**
     * Display the upload form.
     */
    @GetMapping
    public String index(Model model) {
        //model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        model.addAttribute("title", "Category Tree Parser");
        return "products/categories/category-tree-parser"; // Thymeleaf template: upload.html
    }

    /**
     * Handle the file upload, parse the Excel file, and display the category tree.
     */

    @PostMapping("/process")
    public String process(@RequestParam MultipartFile file,
                          Model m,
                          HttpSession session,
                          RedirectAttributes ra) throws IOException, MissingSheetException {

        Path tmp = Files.createTempFile("catUpload", ".xlsx");
        Files.copy(file.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);

        CategoryTreeParserService.CategoryPreviewContext preview = categoryTreeParserService.previewChanges(tmp);
        List<CategoryTreeParserService.CategoryChange> diff = preview.changes();

        if (diff.isEmpty()) {
            ra.addFlashAttribute("message", "No changes detected.");
            m.addAttribute("categoryTree", preview.categoryTree());
            //m.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
            m.addAttribute("title", "Category Tree Parser");
            return "products/categories/category-tree-parser";
        }

        // store both the diff and the DB map in the session
        session.setAttribute("pendingFile", tmp);
        session.setAttribute("pendingDbMap", preview.dbMap());
        session.setAttribute("pendingChanges", diff);

        m.addAttribute("changes", diff);
        m.addAttribute("categoryTree", preview.categoryTree());
        //m.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        m.addAttribute("title", "Category Tree Differences");

        return "products/categories/category-tree-review";
    }


    /**
     * STEP 2 – COMMIT: Apply the pending category changes to the database.
     */
    @PostMapping("/apply")
    public String apply(HttpSession session, RedirectAttributes ra) {
        @SuppressWarnings("unchecked")
        List<CategoryTreeParserService.CategoryChange> changes =
                (List<CategoryTreeParserService.CategoryChange>) session.getAttribute("pendingChanges");

        @SuppressWarnings("unchecked")
        Map<String, Category> dbMap = (Map<String,Category>) session.getAttribute("pendingDbMap");
        Path file = (Path) session.getAttribute("pendingFile");

        if (changes == null || changes.isEmpty()) {
            ra.addFlashAttribute("error", "No pending changes found. Please upload and review again.");
            return "redirect:/products/categories/category-tree-parser";
        }

        try {
            categoryTreeParserService.applyChanges(changes, dbMap);
            ra.addFlashAttribute("message", "Category changes have been applied successfully.");
        } catch (Exception ex) {

            logger.error("Failed to apply category changes", ex);

            // 2) extract the *deepest* cause so the message is useful
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }

            // 3) push a short error summary AND the full stack trace
            ra.addFlashAttribute("error",
                    root.getClass().getSimpleName() + " – " + root.getMessage());
            ra.addFlashAttribute("stackTrace",
                    Arrays.toString(root.getStackTrace()));

        } finally {
            // cleanup must always run
            session.removeAttribute("pendingFile");
            session.removeAttribute("pendingChanges");
            session.removeAttribute("pendingDbMap");
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // you might choose to log this if you need to know about delete failures
            }
        }
        return "redirect:/products/categories/category-tree-parser";
    }
}
