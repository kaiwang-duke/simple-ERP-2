package com.nineforce.service.product.category;

import com.nineforce.exception.MissingSheetException;
import com.nineforce.model.product.category.Category;
import com.nineforce.repository.product.category.CategoryRepository;
import com.nineforce.util.ExcelUtil;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;

import static java.util.stream.Collectors.toMap;
import static com.nineforce.util.StringUtil.trimToEmpty;
import static com.nineforce.service.product.category.CategoryPathUtil.bracketedCnName;

@Service
public class CategoryTreeParserService {

    static final Logger logger = org.slf4j.LoggerFactory.getLogger(CategoryTreeParserService.class);

    // Define starting column index
    private static final int CAT1_EN_CELL = 11;
    private static final int CAT1_CN_CELL = CAT1_EN_CELL + 1;
    private static final int CAT2_EN_CELL = CAT1_EN_CELL + 2;
    private static final int CAT2_CN_CELL = CAT1_EN_CELL + 3;
    private static final int CAT3_EN_CELL = CAT1_EN_CELL + 4;
    private static final int CAT3_CN_CELL = CAT1_EN_CELL + 5;
    private static final int CAT4_IS_SEASONAL_CELL = CAT1_EN_CELL + 6;
    private static final int CAT5_START_SELLING_MONTH_CELL = CAT1_EN_CELL + 7;
    private static final int CAT6_END_SELLING_MONTH_CELL = CAT1_EN_CELL + 8;
    private static final int CAT7_PURCHASE_MONTH_CELL = CAT1_EN_CELL + 9;

    private final CategoryRepository repo;
    private final CategoryDiffer differ;

    @Autowired
    public CategoryTreeParserService(CategoryRepository categoryRepository, CategoryDiffer differ) {
        this.repo = categoryRepository;
        this.differ = differ;
    }

    /* ------------ STEP 1 – PREVIEW ------------ */
    public CategoryPreviewContext previewChanges(Path excel) throws IOException, MissingSheetException {
        // 1) Parse sheet rows
        List<ParsedRow> rows = parseCategory(excel);   // tiny helper below

        String categoryTree = CategoryTreeListService.buildUnicodeTreeString(buildCategoryTree(rows)); // for testing

        // 2) Build “DB map”
        Map<String, Category> dbSnapshot = repo.findAllWithHierarchy().stream()
                //.peek(c -> logger.debug("DB-> {}", CategoryPathUtil.pathOf(c))) // tiny breadcrumb
                .collect( toMap(CategoryPathUtil::pathOf, Function.identity()));


        // 3) Diff (delegated to helper)
        List<CategoryChange> out = differ.diff(rows, dbSnapshot);

        return new CategoryPreviewContext(out, dbSnapshot, categoryTree); // return both the diff and the DB snapshot
    }


    /* ------------------------------------------------------------------ */
    /* tiny helper DTOs   Data Transfer Object          */
    public record ParsedRow(String grand, String grandCn,
                            String parent, String parentCn,
                            String child, String childCn,
                            boolean seasonal,Integer start,Integer end,Integer purchase){

        boolean isSameAs(Category c) {
            if (c == null) return false;

            int lvl = levelOf();                       // 1, 2, or 3

            boolean sameEn = enAtLevel(lvl).equalsIgnoreCase(trimToEmpty(c.getName()));
            boolean sameCn = cnAtLevel(lvl).equalsIgnoreCase(trimToEmpty(c.getCnName()));
            boolean sameSeasonal = seasonal == c.isSeasonal();
            boolean sameStart    = Objects.equals(start,    c.getStartSellingMonth());
            boolean sameStop     = Objects.equals(end,      c.getStopSellingMonth());
            boolean samePurchase = Objects.equals(purchase, c.getPurchaseMonth());

            boolean ok = sameEn && sameCn && sameSeasonal
                    && sameStart && sameStop && samePurchase;

            if (!ok && logger.isDebugEnabled()) {
                logger.debug("""
            ≠  DIFF at [{}]
               Excel → en={} cn={} seas={} start={} stop={} purchase={}
               DB    → en={} cn={} seas={} start={} stop={} purchase={}""",
                        CategoryPathUtil.pathOf(grand(), parent(), child()),
                        enAtLevel(lvl), cnAtLevel(lvl), seasonal, start, end, purchase,
                        trimToEmpty(c.getName()), trimToEmpty(c.getCnName()),
                        c.isSeasonal(), c.getStartSellingMonth(),
                        c.getStopSellingMonth(), c.getPurchaseMonth());
            }
            return ok;
        }


        /* ---------- helpers -------------------------------------------------- */
         int levelOf() {
            if (!trimToEmpty(child()).isEmpty())  return 3;   // Bike → Lock → cable
            if (!trimToEmpty(parent()).isEmpty()) return 2;   // Bike → Lock
            return 1;                                         // Bike
        }

        String enAtLevel(int level) {
            return switch (level) {
                case 3 -> trimToEmpty(child());
                case 2 -> trimToEmpty(parent());
                default -> trimToEmpty(grand());
            };
        }
        String cnAtLevel( int level) {
            return switch (level) {
                case 3 -> trimToEmpty(childCn());
                case 2 -> trimToEmpty(parentCn());
                default -> trimToEmpty(grandCn());
            };
        }
    }

    public enum ChangeType{ADD,UPDATE,DELETE}

    /**
     * STEP 2 – COMMIT: Apply a list of CategoryChange objects to the database.
     */
    @Transactional
    public void applyChanges(List<CategoryChange> changes,
                             Map<String,Category> dbMap) {

        for (CategoryChange ch : changes) {
            switch (ch.type()) {
                case ADD -> {
                    String fullPath = ch.path();

                    // 1) find parent (same code you already had)
                    Category parent = null;
                    if (fullPath.contains(" -> ")) {                  // keep the spaces, matches the real separator
                        String parentPath = fullPath
                                .substring(0, fullPath.lastIndexOf(" -> "))
                                .trim();
                        // what if we see child before parent?
                        parent = dbMap.get(parentPath);               // will succeed after we fix the cache
                    }

                    // 2) create & save
                    if (ch.name().isBlank()) {
                        logger.warn("Skipping row with blank leaf name at path '{}'", fullPath);
                        break;                                        // or `continue;`
                    }

                    Category newCat = new Category();
                    newCat.setName(ch.name());
                    newCat.setCnName(ch.cnName());
                    newCat.setSeasonal(ch.seasonal());
                    newCat.setStartSellingMonth(ch.startMonth());
                    newCat.setStopSellingMonth(ch.stopMonth());
                    newCat.setPurchaseMonth(ch.purchaseMonth());
                    newCat.setParent(parent);
                    save(newCat, fullPath);
                    //repo.save(newCat);

                    // 3) ***critical*** – cache it so that its children pick it up
                    dbMap.put(fullPath, newCat);
                }

                case UPDATE -> {
                    if (ch.oldLine()) continue;  // skip the “old” preview line

                    // instead of repo.findByNameAndParent(…) or findByPath(…), just look up in dbMap:
                    Category existing = dbMap.get(ch.path());
                    if (existing == null) {
                        throw new RuntimeException("Cannot find existing category: " + ch.path());
                    }
                    existing.setName(ch.name());
                    existing.setCnName(ch.cnName());
                    existing.setSeasonal(ch.seasonal());
                    existing.setStartSellingMonth(ch.startMonth());
                    existing.setStopSellingMonth(ch.stopMonth());
                    existing.setPurchaseMonth(ch.purchaseMonth());
                    //repo.save(existing);
                    save(existing, ch.path());
                }

                case DELETE -> {
                    // similarly, find the Category to delete via dbMap
                    Category toDelete = dbMap.get(ch.path());
                    if (toDelete == null) {
                        throw new RuntimeException("Cannot find category to delete: " + ch.path());
                    }
                    repo.delete(toDelete);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  save-or-throw – enriches DataIntegrityViolationException with
     *  the category’s *English name* and its *full path* so the UI
     *  tells you exactly what collided.                                  */
    /* ------------------------------------------------------------------ */
    private void save(Category entity, String fullPath) {
        try {
            repo.save(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            String msg = "Duplicate category detected → path = \"" + fullPath +
                    "\", name = \"" + entity.getName() + "\"";
            // 1) log the enriched message plus stacktrace
            logger.error(msg, ex);
            // 2) re-throw – we keep the original exception as cause so nothing else changes
            throw new RuntimeException(msg, ex);
        }
    }




    /**
     * Builds a hierarchical category tree from the provided list of ParsedRow objects.
     * This is for display tree structure in the UI, not for database operations.
     */
    public SortedMap<String, SortedMap<String, SortedSet<String>>> buildCategoryTree(List<ParsedRow> rows) {
        SortedMap<String, SortedMap<String, SortedSet<String>>> tree = new TreeMap<>();
        for (ParsedRow r : rows) {
            String cat1Key = r.grand() + bracketedCnName(r.grandCn());
            tree.putIfAbsent(cat1Key, new TreeMap<>());
           // only add level-2 key when a real parent exists
           if (r.parent() != null && !r.parent().isBlank()) {
                String cat2Key = r.parent() + bracketedCnName(r.parentCn());
                SortedMap<String, SortedSet<String>> level2Map = tree.get(cat1Key);
                level2Map.putIfAbsent(cat2Key, new TreeSet<>());

                if (r.child() != null && !r.child().isBlank()) {
                    String cat3Val = r.child() + bracketedCnName(r.childCn());
                    level2Map.get(cat2Key).add(cat3Val);
                }
            }
        }
        return tree;
    }


    private static Integer safeMonth(Integer value) {
        return (value == null || value <= 0) ? 1 : value;
    }

    /* ---------------------------------------------------------------
     * Tiny helper that returns a List<ParsedRow> from the Excel file.
     * ------------------------------------------------------------- */
    protected static List<ParsedRow> parseCategory(Path file) throws IOException, MissingSheetException {
        List<ParsedRow> out;
        out = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file.toFile());
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet("product");
            if (sheet == null)
                throw new MissingSheetException("Required sheet 'product' is missing.");

            for (Row r : sheet) {
                if (r.getRowNum()==0) continue;         // skip header

                String c1 = ExcelUtil.getCellStringValue((XSSFRow) r, CAT1_EN_CELL).trim();
                String c2 = ExcelUtil.getCellStringValue((XSSFRow) r, CAT2_EN_CELL).trim();
                String c3 = ExcelUtil.getCellStringValue((XSSFRow) r, CAT3_EN_CELL).trim();

                if (c1.isEmpty() ) {
                    logger.warn("At least level-1 category name is required.");
                    continue; // need at least L1+L2
                }

                out.add(new ParsedRow(
                        c1,                    ExcelUtil.getCellStringValue((XSSFRow) r, CAT1_CN_CELL).trim(),
                        c2.isEmpty()?null:c2,  ExcelUtil.getCellStringValue((XSSFRow) r, CAT2_CN_CELL).trim(),
                        c3.isEmpty()?null:c3,  ExcelUtil.getCellStringValue((XSSFRow) r, CAT3_CN_CELL).trim(),

                        ExcelUtil.getCellBooleanValue((XSSFRow) r, CAT4_IS_SEASONAL_CELL),
                        safeMonth(ExcelUtil.getCellIntValue((XSSFRow) r, CAT5_START_SELLING_MONTH_CELL)),
                        safeMonth(ExcelUtil.getCellIntValue((XSSFRow) r, CAT6_END_SELLING_MONTH_CELL)),
                        safeMonth(ExcelUtil.getCellIntValue((XSSFRow) r, CAT7_PURCHASE_MONTH_CELL))
                ));
            }
        }
        return out;
    }

    /** View-friendly DTO – one field per table column */
    public record CategoryChange(
            ChangeType type,
            String   path,
            String   name,
            String   cnName,
            boolean  seasonal,
            Integer  startMonth,
            Integer  stopMonth,
            Integer  purchaseMonth,
            boolean  oldLine){
        public boolean oldLine() { return oldLine; } // for consistency with CategoryChangeService
    }

    /**
     +     * Holds both the diff and the “DB snapshot” and the rendered tree string.
     +     */
     public record CategoryPreviewContext(
        List<CategoryChange> changes,
        Map<String,Category> dbMap,
        String               categoryTree
    ) {

    }

}
