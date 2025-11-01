package com.nineforce.controller.product.category;  // adjust to your actual package

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.nineforce.model.product.category.Category;
import com.nineforce.service.product.category.CategoryTableListService;
//import com.nineforce.util.FirebaseAuthUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/products/categories")
@RequiredArgsConstructor    // Lombok auto-generates the constructor
public class CategoryTableListController {

    private final CategoryTableListService svc;              // immutable
    //private final FirebaseAuthUtil firebaseAuthUtil; // immutable

    /* ========== LIST ========= */
    // handle both “/products/category” and “/products/category/category-table-list”
    @GetMapping({ "", "/category-table-list" })
    public String list(
            @RequestParam Optional<String> q,
            @PageableDefault(sort="name",direction=Sort.Direction.ASC) Pageable page,
            Model model){
        model.addAttribute("page", svc.list(q.orElse(""), page));
        model.addAttribute("q", q.orElse(""));

        //model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        model.addAttribute("title", "Category Table List");
        return "products/categories/category-table-list";
    }

    /* ========== CREATE / EDIT FORM ========= */
    @GetMapping({"/new", "/{categoryId}/edit"})
    public String form(@PathVariable(required=false) Integer categoryId, Model m){
        m.addAttribute("category", categoryId==null? new Category(): svc.get(categoryId));
        m.addAttribute("roots", svc.roots());
        //String userEmail = firebaseAuthUtil.getUserEmail();
        //m.addAttribute("userEmail", userEmail);
        return "products/categories/category-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Category c, RedirectAttributes ra){
        Category saved = svc.save(c);

        String parentName = saved.getParent() != null
                ? saved.getParent().getName()
                : "(none)";

        String details = String.format(
                "ID=%d, name=\"%s\", cnName=\"%s\", parent=\"%s\", seasonal=%b, "
                        + "purchaseMonth=%d, startSellingMonth=%d, stopSellingMonth=%d",
                saved.getId(),
                saved.getName(),
                saved.getCnName(),
                parentName,
                saved.isSeasonal(),
                saved.getPurchaseMonth(),
                saved.getStartSellingMonth(),
                saved.getStopSellingMonth()
        );

        ra.addFlashAttribute("msg","Saved!");
        ra.addFlashAttribute("details",details);
        return "redirect:/products/categories";
    }

    /* ========== DELETE ========= */
    @PostMapping("/{categoryId}/delete")
    public String delete(@PathVariable int categoryId, RedirectAttributes ra){
        svc.delete(categoryId);
        ra.addFlashAttribute("msg","Deleted!");
        return "redirect:/products/categories";
    }


    @GetMapping("/export")
    public void export(
            @RequestParam(required=false) String q,
            Pageable pageable,
            HttpServletResponse resp) throws IOException
    {
        // re-use your list(...) logic to get exactly the current view
        Page<Category> page = svc.list(q, pageable);
        List<Category> cats = page.getContent();

        // build & write workbook
        resp.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        resp.setHeader("Content-Disposition",
                "attachment; filename=category.xlsx"
        );
        try(XSSFWorkbook wb = svc.buildExcel(cats)) {
            wb.write(resp.getOutputStream());
        }
    }
}
