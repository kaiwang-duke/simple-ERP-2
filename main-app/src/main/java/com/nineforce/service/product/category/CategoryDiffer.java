package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import static java.util.stream.Collectors.toMap;

import static com.nineforce.util.StringUtil.trimToEmpty;
import static com.nineforce.util.StringUtil.defaultIfNullOrBlank;

import static com.nineforce.service.product.category.CategoryPathUtil.SEP;
import static com.nineforce.service.product.category.CategoryTreeParserService.*;
import static com.nineforce.service.product.category.CategoryTreeParserService.ChangeType.*;

@Component
public class CategoryDiffer {

    /**
     * Compare each ParsedRow (from Excel) against the dbMap snapshot,
     * producing ADD, UPDATE (new + old), and DELETE changes.
     */
    public List<CategoryChange> diff(
            List<CategoryTreeParserService.ParsedRow> rows,
            Map<String,Category> dbSnapshot
    ) {
        var sheetMap      = buildSheetMap(rows);
        var withAncestors = synthesizeAncestors(sheetMap, rows, dbSnapshot);
        Map<String,Category> workDb = new HashMap<>(dbSnapshot);

        List<CategoryChange> result = new ArrayList<>();
        result.addAll(computeAddsAndUpdates(withAncestors, workDb));
        result.addAll(computeDeletes(withAncestors.keySet(), workDb));

        return sortByRules(result);
    }


    // 1) buildSheetMap
    Map<String,ParsedRow> buildSheetMap(List<ParsedRow> rows) {
        return rows.stream().collect(toMap(
                r -> CategoryPathUtil.pathOf(r.grand(),r.parent(),r.child()),
                Function.identity(),
                (a,b)->a
        ));
    }

    // 2) synthesize Ancestors  grand -> parent -> child. Add ancestors if not present.
    Map<String,ParsedRow> synthesizeAncestors(
            Map<String,ParsedRow> sheetMap,
            List<ParsedRow> rows,
            Map<String,Category> dbSnapshot ) {

        for (ParsedRow r : rows) {
            String full = CategoryPathUtil.pathOf(r.grand(),r.parent(),r.child());
            for (String anc : CategoryPathUtil.prefixPaths(full)) {

                // ∴ duplicates suppressed. Multiple child only bring one parent.
                sheetMap.computeIfAbsent(anc, pathKey -> {

                    Category dbCat = dbSnapshot.get(pathKey);
                    int depth = pathKey.split(CategoryPathUtil.SEP_REGEX, -1).length;

                    // CASE 1: ancestor already in DB – clone its values, NO UPDATE later
                    Category inDb = dbSnapshot.get(anc);
                    if (inDb != null) {
                        ParsedRow base  = buildRowForDepth(dbCat, depth);
                        return overlayCn(base, r, depth);
                    }

                    // CASE 2: brand-new ancestor – minimal placeholder
                    String[] parts = pathKey.split(CategoryPathUtil.SEP_REGEX);
                    String g = parts[0];
                    String pr = parts.length>1 ? parts[1] : null;
                    return new ParsedRow(g, r.grandCn(),
                            pr, pr!=null ? r.parentCn():null,
                            null, null,
                            false, null, null, null);   // ← no seasonal / month data
                });
            }
        }
        return sheetMap;
    }

    static ParsedRow buildRowForDepth(Category cat, int depth) {
        return switch (depth) {

            // depth-1  (grand only)
            case 1 -> new ParsedRow(
                    cat.getName(),   cat.getCnName(),   // grand
                    null, null,                         // parent
                    null, null,                         // child
                    cat.isSeasonal(),
                    cat.getStartSellingMonth(),
                    cat.getStopSellingMonth(),
                    cat.getPurchaseMonth());

            // depth-2  (grand + parent)
            case 2 -> {
                Category grand = cat.getParent();        // grand exists at depth-2
                yield new ParsedRow(
                        grand.getName(), grand.getCnName(), // grand
                        cat.getName(),   cat.getCnName(),   // parent
                        null, null,                         // child
                        cat.isSeasonal(),
                        cat.getStartSellingMonth(),
                        cat.getStopSellingMonth(),
                        cat.getPurchaseMonth());
            }

            // anything else would be a bug
            default -> throw new IllegalArgumentException("Unexpected depth: " + depth);
        };

    }

    /** If the sheet provides a CN-name at this depth, overwrite the DB clone. */
    static ParsedRow overlayCn(ParsedRow base, ParsedRow sheet, int depth) {

        switch (depth) {
            case 1 -> {   // grand only
                String grandCn = defaultIfNullOrBlank(sheet.grandCn(), base.grandCn());
                return new ParsedRow(base.grand(), grandCn,
                        null, null, null, null,
                        base.seasonal(), base.start(),
                        base.end(), base.purchase());
            }
            case 2 -> {   // grand + parent
                String parentCn = defaultIfNullOrBlank(sheet.parentCn(), base.parentCn());
                return new ParsedRow(base.grand(),  base.grandCn(),
                        base.parent(), parentCn,
                        null, null,
                        base.seasonal(), base.start(),
                        base.end(), base.purchase());
            }
            default -> {  // depth-3 row never hits overlay
                return base;
            }
        }
    }

    // 3) computeAddsAndUpdates
    List<CategoryChange> computeAddsAndUpdates(
            Map<String,ParsedRow> sheetMap,
            Map<String,Category> workDb
    ) {
        List<CategoryChange> out = new ArrayList<>();

        for (var extendedParsedRow : sheetMap.entrySet()) {
            String path = extendedParsedRow.getKey();
            ParsedRow row = extendedParsedRow.getValue();
            Category existing = workDb.remove(path);

            if (existing==null) {
              // New category, add to DB, but don't add > 1 time
              // return true
              out.add(createExcelChange(ADD, path, row));
            } else if (!row.isSameAs(existing)) {
              out.add(createExcelChange(UPDATE, path, row));
              out.add(createDbChange(UPDATE, path, existing, true));
            }
        }
        return out;
    }

    // 4) computeDeletes
    List<CategoryChange> computeDeletes(
            Set<String> sheetKeys,
            Map<String,Category> workDb
    ) {
        List<CategoryChange> out = new ArrayList<>();
        for (String leftover : workDb.keySet()) {
            boolean hasDesc = sheetKeys.stream()
                    .anyMatch(s -> s.startsWith(leftover + SEP));
            if (!hasDesc) {
                out.add(createDbChange(DELETE,leftover,workDb.get(leftover),false));
            }
        }
        return out;
    }

    // 5) sortByRules
    /** Sorts DELETE ➜ ADD ➜ UPDATE, then applies type-specific comparators.
        * <p>
        * DELETE: child → parent, then grand
        * ADD / UPDATE: grand first, then parent → child
        * </p>
        *
        * @param src the list of changes to sort
        * @return sorted list of changes
        */
    /* ---------------------------------------------------------------------- */

    static List<CategoryChange> sortByRules(List<CategoryChange> src) {
        return src.stream()
                .sorted(comparator())   // ← no local “main” variable anymore
                .toList();
    }

    /** Master comparator: group DELETE→ADD→UPDATE, then depth-aware ordering. */
    private static Comparator<CategoryChange> comparator() {

        Comparator<CategoryChange> depthAsc  = Comparator.comparingInt(ch -> depth(ch.path()));
        Comparator<CategoryChange> depthDesc = depthAsc.reversed();
        Comparator<CategoryChange> alpha     = Comparator.comparing(CategoryChange::path,
                String.CASE_INSENSITIVE_ORDER);

        Comparator<CategoryChange> cmpAddUpd = alpha.thenComparing(depthAsc);   // parent → child
        Comparator<CategoryChange> cmpDel    = depthDesc.thenComparing(alpha);  // child  → parent

        return Comparator
                .comparingInt(CategoryDiffer::groupKey)        // group order
                .thenComparing((c1, c2) -> {                   // inside group
                    Comparator<CategoryChange> sub =
                            (c1.type() == ChangeType.DELETE) ? cmpDel : cmpAddUpd;
                    return sub.compare(c1, c2);
                });
    }



    static int groupKey(CategoryChange ch) {
        return switch (ch.type()) {
            case DELETE -> 0;
            case ADD    -> 1;
            case UPDATE -> 2;
        };
    }

    /** Helper: how many segments in a category path. */
    static int depth(String path) {
        return path.isBlank() ? 0
                : path.split(CategoryPathUtil.SEP_REGEX, -1).length;
    }


    private CategoryChange createExcelChange(
            CategoryTreeParserService.ChangeType type,
            String path,
            CategoryTreeParserService.ParsedRow row
    ) {
        int lvl = row.levelOf();
        return new CategoryTreeParserService.CategoryChange(
                type,
                path,
                row.enAtLevel(lvl), row.cnAtLevel(lvl),
                row.seasonal(), row.start(), row.end(), row.purchase(),
                false
        );
    }

    private CategoryTreeParserService.CategoryChange createDbChange(
            CategoryTreeParserService.ChangeType type,
            String       path,
            Category     cat,
            boolean      oldLine
    ) {
        return new CategoryTreeParserService.CategoryChange(
                type,
                path,
                trimToEmpty(cat.getName()),
                trimToEmpty(cat.getCnName()),
                cat.isSeasonal(),
                cat.getStartSellingMonth(),
                cat.getStopSellingMonth(),
                cat.getPurchaseMonth(),
                oldLine
        );
    }
}
