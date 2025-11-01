package com.nineforce.service.product.category;


import com.nineforce.model.product.category.Category;
import com.nineforce.repository.product.category.CategoryRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CategoryTableListService {

    private final CategoryRepository repo;

    public CategoryTableListService(CategoryRepository repo) { this.repo = repo; }

    /* ---------- CRUD ---------- */
    public Category get(int id) {                          // Read one
        return repo.findById(id).orElseThrow(() ->
                new NoSuchElementException("Category "+id+" not found"));
    }
    public Category save(Category c){ return repo.save(c);}  // Create / Update

    // TODO Delete all products in this category first
    public void delete(int id){ repo.deleteById(id);}        // Delete


    /* ---------- List / search / sort ---------- */
    public Page<Category> list(String q, Pageable pageable) {

        // ==== special case: sort on computed "path" in memory ====
        Sort.Order pathOrder = pageable.getSort().getOrderFor("path");
        if (pathOrder != null) {

            // 1) fetch all matching rows (no DB sort/limit)
            List<Category> all = (q != null && !q.isBlank())
                    ? new ArrayList<>(repo.findByNameContainingIgnoreCaseWithHierarchy(q))
                    : new ArrayList<>(repo.findAllWithHierarchy());   //make them mutable

            // 2) in-memory sort
            Comparator<Category> cmp = Comparator.comparing(
                    Category::getPath,
                    Comparator.nullsFirst(String::compareToIgnoreCase)
            );
            if (pathOrder.isDescending()) cmp = cmp.reversed();
            all.sort(cmp);

            // 3) slice to requested page & wrap in PageImpl
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), all.size());
            List<Category> slice = (start >= all.size()) ? List.of() : all.subList(start, end);
            return new PageImpl<>(slice, pageable, all.size());
        }

        // ==== normal case: let Spring Data + DB handle paging/sorting ====
        if (q != null && !q.isBlank())
            return repo.findByNameContainingIgnoreCase(q, pageable);
        return repo.findAll(pageable);
    }

    /* ---------- helpers for forms ---------- */
    public List<Category> roots(){ return repo.findByParentIsNullOrderByName(); }

    public String getCategoryPath(Long categoryId) {
        // 1) fetch the category + one-level parent in one SQL round-trip:
        Category cur = repo.findByIdWithParent(categoryId)
                .orElseThrow(() -> new NoSuchElementException("Category not found"));

        // 2) walk up to the root, collecting names
        List<String> parts = new ArrayList<>();
        while (cur != null) {
            parts.add(cur.getName());
            cur = cur.getParent();
        }

        // 3) reverse and join with arrows
        Collections.reverse(parts);
        return String.join(" -> ", parts);
    }



    public XSSFWorkbook buildExcel(List<Category> cats) {
        XSSFWorkbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet("Categories");

        // 1) header row
        var header = sheet.createRow(0);
        String[] cols = {
                "ID","Name","CN Name","Parent","Seasonal",
                "Purchase Month","Start Month","Stop Month","Path"
        };
        for(int i=0; i<cols.length; i++){
            header.createCell(i).setCellValue(cols[i]);
        }

        // 2) data rows
        for(int r=0; r<cats.size(); r++){
            Category c = cats.get(r);
            var row = sheet.createRow(r+1);

            row.createCell(0).setCellValue(c.getId());
            row.createCell(1).setCellValue(c.getName());
            row.createCell(2).setCellValue(c.getCnName());
            row.createCell(3).setCellValue(
                    c.getParent() != null ? c.getParent().getName() : ""
            );
            row.createCell(4).setCellValue(c.isSeasonal());

            // Numeric month values
            row.createCell(5).setCellValue(
                    c.isSeasonal() ? c.getPurchaseMonth() : 0
            );
            row.createCell(6).setCellValue(
                    c.isSeasonal() ? c.getStartSellingMonth(): 0
            );
            row.createCell(7).setCellValue(
                    c.isSeasonal() ? c.getStopSellingMonth(): 0
            );

            // computed path
            row.createCell(8).setCellValue(getCategoryPath(c.getId().longValue()));
        }

        // auto-size columns for neatness
        for(int i=0; i<cols.length; i++){
            sheet.autoSizeColumn(i);
        }
        return wb;
    }

}
