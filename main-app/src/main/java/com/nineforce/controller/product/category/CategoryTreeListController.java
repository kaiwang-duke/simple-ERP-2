package com.nineforce.controller.product.category;

import com.nineforce.service.product.category.CategoryTreeListService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.SortedMap;
import java.util.SortedSet;

//import com.nineforce.util.FirebaseAuthUtil;

@Controller
@RequestMapping("/products/categories/category-tree-list")
public class CategoryTreeListController {

    private final CategoryTreeListService categoryTreeListService;
    //private final FirebaseAuthUtil firebaseAuthUtil;

    public CategoryTreeListController(CategoryTreeListService categoryTreeListService) {
                                      //FirebaseAuthUtil firebaseAuthUtil) {
        this.categoryTreeListService = categoryTreeListService;
        //this.firebaseAuthUtil = firebaseAuthUtil;
    }

    @GetMapping
    public String showCategoryTree(Model model) {
        SortedMap<String, SortedMap<String, SortedSet<String>>> categoryTree = categoryTreeListService.getCategoryTree();
        model.addAttribute("categoryTree", categoryTree);

        String unicodeTreeString = CategoryTreeListService.buildUnicodeTreeString(categoryTree);  // static method call
        model.addAttribute("unicodeTreeString", unicodeTreeString);


        //model.addAttribute("userEmail", firebaseAuthUtil.getUserEmail());
        model.addAttribute("title", "Inventory File Converter");

        return "products/categories/category-tree-list"; // This is the Thymeleaf template name (without .html)
    }
}



