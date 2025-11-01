package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nineforce.util.StringUtil.defaultIfNullOrBlank;
import static com.nineforce.service.product.category.CategoryPathUtil.pathOf;
import static com.nineforce.service.product.category.CategoryTreeParserService.ChangeType.*;
import static com.nineforce.service.product.category.CategoryTreeParserService.CategoryChange;
import static com.nineforce.service.product.category.CategoryTreeParserService.ParsedRow;
import static org.assertj.core.api.Assertions.assertThat;

class CategoryDifferTest {

    private final CategoryDiffer differ = new CategoryDiffer();

    private static Category makeCategory(String name) {
        Category c = new Category();
        c.setName(name);
        return c;
    }

    @Nested
    @DisplayName("ADD scenarios")
    class AddTests {
        @Test
        @DisplayName("When DB empty and Excel has one row → one ADD")
        void addsSingleRow() {
            // Excel has one row: Root -> Child -> Leaf
            var row = new CategoryTreeParserService.ParsedRow(
                    "Root", "根",
                    "Child","子",
                    "Leaf", "叶",
                    false, 1, 12, 6
            );
            List<CategoryTreeParserService.ParsedRow> rows = List.of(row);
            Map<String, Category> db = Map.of();

            List<CategoryTreeParserService.CategoryChange> changes = differ.diff(rows, db);

            assertThat(changes).hasSize(3);
            var ch_add = changes.get(0);
            assertThat(ch_add.type()).isEqualTo(ADD);

            // we synthesize one ADD for each prefix (Root, Root→Child) plus the leaf itself
            assertThat(changes)
                .hasSize(3)
                .extracting(ch -> List.of(ch.path(), ch.name(), ch.cnName(), ch.type()))
                .containsExactly(
                    List.of("Root",                       "Root",  "根", ADD),
                    List.of("Root -> Child",              "Child", "子", ADD),
                    List.of("Root -> Child -> Leaf",      "Leaf",  "叶", ADD)
                );
        }
    }

    @Nested
    @DisplayName("DELETE scenarios")
    class DeleteTests {
        @Test
        @DisplayName("When Excel empty and DB has one entry → one DELETE")
        void deletesSingleDbEntry() {
            // DB has a single top‐level category
            Category root = makeCategory("Alone");
            Map<String, Category> db = Map.of(pathOf(root), root);

            List<CategoryTreeParserService.CategoryChange> changes = differ.diff(List.of(), db);

            assertThat(changes).hasSize(1);
            var ch = changes.get(0);
            assertThat(ch.type()).isEqualTo(DELETE);
            assertThat(ch.path()).isEqualTo("Alone");
            assertThat(ch.name()).isEqualTo("Alone");
            // oldLine always false for DELETE preview
            assertThat(ch.oldLine()).isFalse();
        }
    }

    @Nested
    @DisplayName("NO-OP scenarios")
    class NoOpTests {
        @Test
        @DisplayName("When Excel rows exactly match DB → no changes")
        void noChangesWhenIdentical() {
            // Build a 2-level tree in Excel
            var row = new CategoryTreeParserService.ParsedRow(
                    "Top", "顶",
                    "Sub", "下",
                    null,  null,
                    true, 2, 8, 3
            );
            List<CategoryTreeParserService.ParsedRow> rows = List.of(row);

            // Build a matching Category in DB
            Category top = makeCategory("Top");
            top.setCnName("顶");
            top.setSeasonal(true);
            top.setStartSellingMonth(2);
            top.setStopSellingMonth(8);
            top.setPurchaseMonth(3);

            Category sub = makeCategory("Sub");
            sub.setCnName("下");
            sub.setSeasonal(true);
            sub.setStartSellingMonth(2);
            sub.setStopSellingMonth(8);
            sub.setPurchaseMonth(3);
            sub.setParent(top);

            Map<String,Category> db = Map.of(
                    pathOf(top), top,
                    pathOf(sub), sub
            );

            List<CategoryTreeParserService.CategoryChange> changes = differ.diff(rows, db);
            assertThat(changes).isEmpty();
        }
    }

    @Nested
    @DisplayName("UPDATE scenarios")
    class UpdateTests {
        @Test
        @DisplayName("When Excel vs DB differ → one NEW + one OLD")
        void updateProducesTwoLines() {
            // Excel row has different CN than DB
            var row = new CategoryTreeParserService.ParsedRow(
                    "Only", "仅",
                    null,   null,
                    null,   null,
                    false, 1, 1, 1
            );
            List<CategoryTreeParserService.ParsedRow> rows = List.of(row);

            // DB category has same English but blank CN
            Category only = makeCategory("Only");
            only.setCnName("");        // mismatch
            only.setSeasonal(false);
            only.setStartSellingMonth(1);
            only.setStopSellingMonth(1);
            only.setPurchaseMonth(1);

            Map<String,Category> db = Map.of(pathOf(only), only);

            List<CategoryTreeParserService.CategoryChange> changes = differ.diff(rows, db);

            // We expect two lines: first NEW (purple), then OLD (grey)
            assertThat(changes).hasSize(2);

            var newLine = changes.get(0);
            assertThat(newLine.type()).isEqualTo(UPDATE);
            assertThat(newLine.oldLine()).isFalse();
            assertThat(newLine.cnName()).isEqualTo("仅");

            var oldLine = changes.get(1);
            assertThat(oldLine.type()).isEqualTo(UPDATE);
            assertThat(oldLine.oldLine()).isTrue();
            assertThat(oldLine.cnName()).isEqualTo("");
        }
    }


    @Nested
    @DisplayName("MIXED ADD & DELETE scenarios")
    class MixedAddDeleteTests {
        @Test
        @DisplayName("When Excel tree has one branch, DB has another → adds new branch and deletes old")
        void mixedAddAndDelete() {
            // DB: Root → OldChild → OldLeaf
            Category root     = makeCategory("Root");
            Category oldChild = makeCategory("OldChild"); oldChild.setParent(root);
            Category oldLeaf  = makeCategory("OldLeaf");  oldLeaf.setParent(oldChild);

            Map<String,Category> db = Map.of(
                    pathOf(root),     root,
                    pathOf(oldChild), oldChild,
                    pathOf(oldLeaf),  oldLeaf
            );

            // Excel: Root → NewChild → NewLeaf
            var row = new CategoryTreeParserService.ParsedRow(
                    "Root","根",
                    "NewChild","新子",
                    "NewLeaf","新华",
                    false, 1, 12, 6
            );
            List<CategoryTreeParserService.ParsedRow> rows = List.of(row);

            var changes = differ.diff(rows, db);

            // Expect two ADDs for the new branch and two DELETE for the old
            // and 2 UPDATES for the root (new and old compared)
            assertThat(changes)
                    .extracting(ch -> List.of(ch.type(), ch.path(), ch.oldLine()))
                    .containsExactly(
                            // 1‒2  DELETE branch that disappeared
                            List.of(DELETE, "Root -> Oldchild -> Oldleaf", false),
                            List.of(DELETE, "Root -> Oldchild",            false),

                            // 3‒4  ADD branch that is new
                            List.of(ADD,    "Root -> Newchild",            false),
                            List.of(ADD,    "Root -> Newchild -> Newleaf", false),

                            // 5‒6  UPDATE pair on Root (Excel new first, DB old second)
                            List.of(UPDATE, "Root",                        false), // new (CN = 根)
                            List.of(UPDATE, "Root",                        true)   // old (CN = "")
                    );

        }
    }

    @Nested
    @DisplayName("PURE LEAF UPDATE scenarios")
    class LeafUpdateTests {
        @Test
        @DisplayName("When only a leaf’s purchase month changed → only that leaf gets an UPDATE+OLD")
        void updateLeafOnly() {
            // DB: Root → Child → Leaf (purchaseMonth = 5)
            Category root  = makeCategory("Root");
            Category child = makeCategory("Child"); child.setParent(root);
            Category leaf  = makeCategory("Leaf");  leaf.setParent(child);
            leaf.setPurchaseMonth(5);

            Map<String,Category> db = Map.of(
                    pathOf(root),  root,
                    pathOf(child), child,
                    pathOf(leaf),  leaf
            );

            // Excel: same path, but purchaseMonth = 6
            var row = new CategoryTreeParserService.ParsedRow(
                    "Root","",
                    "Child","",
                    "Leaf","",
                    false, 1, 12, 6
            );
            List<CategoryTreeParserService.ParsedRow> rows = List.of(row);

            var changes = differ.diff(rows, db);

            // Exactly two lines: NEW then OLD for the Leaf only
            assertThat(changes).hasSize(2);

            var newLine = changes.get(0);
            assertThat(newLine.type()).isEqualTo(UPDATE);
            assertThat(newLine.oldLine()).isFalse();
            assertThat(newLine.path()).isEqualTo("Root -> Child -> Leaf");
            assertThat(newLine.purchaseMonth()).isEqualTo(6);

            var oldLine = changes.get(1);
            assertThat(oldLine.type()).isEqualTo(UPDATE);
            assertThat(oldLine.oldLine()).isTrue();
            assertThat(oldLine.purchaseMonth()).isEqualTo(5);
        }
    }  // end of LeafUpdateTests


    /* -----------------------------------------------------------------------
     *  EXTRA COVERAGE
     * ---------------------------------------------------------------------*/
    @Nested
    @DisplayName("EDGE-CASE scenarios")
    class EdgeCases {

        @Test
        @DisplayName("Sibling deletes stay alphabetical, depth-desc afterwards")
        void siblingDeleteOrder() {
            // DB has three top-level category with same prefix.
            Category a = makeCategory("Foo");
            Category b = makeCategory("FooBar");
            Category c = makeCategory("FooBarBaz");
            Map<String,Category> db = Map.of(
                    pathOf(a), a,
                    pathOf(b), b,
                    pathOf(c), c
            );

            List<CategoryChange> changes = differ.diff(List.of(), db);

            assertThat(changes)
                    .extracting(CategoryChange::path)
                    .containsExactly(         // child first, then parent
                            "Foo",                 // shortest
                            "Foobar",             // …
                            "Foobarbaz"          // longest
                    );
        }

        @Test
        @DisplayName("Duplicate rows in Excel still yield exactly one ADD per path")
        void suppressDuplicateAdds() {
            ParsedRow row = new ParsedRow(
                    "X", "", "Y", "", null, null,
                    false, null, null, null);
            List<ParsedRow> rows = List.of(row, row, row);     // same path thrice

            List<CategoryChange> changes = differ.diff(rows, Map.of());
            assertThat(changes)
                    .filteredOn(ch -> ch.type() == ADD)
                    .hasSize(2);           // X  +  X->Y   (no duplicates)
        }

        @Test
        @DisplayName("Seasonal flag difference alone triggers UPDATE pair")
        void updateSeasonalOnly() {
            ParsedRow sheet = new ParsedRow("S", "", null,null,null,null,
                    true, null,null,null);
            Category  dbCat = makeCategory("S");               // isSeasonal=false
            Map<String,Category> db = Map.of(pathOf(dbCat), dbCat);

            List<CategoryChange> changes = differ.diff(List.of(sheet), db);

            assertThat(changes).hasSize(2)
                    .extracting(CategoryChange::oldLine)
                    .containsExactly(false, true);
        }

        @Test
        @DisplayName("Prefix paths synthesised for a new *mid-level* row")
        void synthesiseParentOnly() {
            ParsedRow sheet = new ParsedRow(
                    "A","", "B","", null,null,
                    false,null,null,null);      // only two levels

            List<CategoryChange> changes = differ.diff(List.of(sheet), Map.of());

            assertThat(changes)
                    .extracting(CategoryChange::path, CategoryChange::type)
                    .containsExactly(
                            Tuple.tuple("A",           ADD),   // synthesised
                            Tuple.tuple("A -> B",      ADD));  // direct row
        }

        @Test
        @DisplayName("Update on parent *and* leaf when both differ")
        void parentAndLeafUpdate() {
            // DB tree
            Category p = makeCategory("P"); p.setCnName("父");
            Category l = makeCategory("L"); l.setParent(p); l.setPurchaseMonth(1);
            Map<String,Category> db = Map.of(
                    pathOf(p), p,
                    pathOf(l), l);

            // Excel: parent CN changed, leaf month changed
            ParsedRow sheet = new ParsedRow(
                    "P","改父", "L","", null, null,
                    false,1,12,2);
            List<CategoryChange> changes = differ.diff(List.of(sheet), db);

            // 4 lines: parent NEW+OLD, leaf NEW+OLD
            assertThat(changes)
                    .filteredOn(ch -> ch.type() == UPDATE)
                    .hasSize(4);
        }
    }  // end of EdgeCases



    @Nested
    @DisplayName("INTERNALS & EDGE CASES")
    class InternalsAndEdgeCases {

        @Test
        @DisplayName("overlayCn overlays grand and parent CN correctly")
        void overlayCnOverlaysCorrectly() {
            ParsedRow base = new ParsedRow("G", "g", "P", "p", null, null, false, null, null, null);
            ParsedRow sheet = new ParsedRow("G", "G_CN", "P", "P_CN", null, null, false, null, null, null);

            // depth 1 overlays grand CN
            ParsedRow over1 = CategoryDiffer.overlayCn(base, sheet, 1);
            assertThat(over1.grandCn()).isEqualTo("G_CN");
            // depth 2 overlays parent CN
            ParsedRow over2 = CategoryDiffer.overlayCn(base, sheet, 2);
            assertThat(over2.parentCn()).isEqualTo("P_CN");
            // depth 3 returns base
            ParsedRow over3 = CategoryDiffer.overlayCn(base, sheet, 3);
            assertThat(over3).isSameAs(base);
        }

        @Test
        @DisplayName("overlayCn throws on negative depth")
        void overlayCnThrowsOnNegativeDepth() {
            ParsedRow base = new ParsedRow("G", "g", null, null, null, null, false, null, null, null);
            ParsedRow sheet = new ParsedRow("G", "G_CN", null, null, null, null, false, null, null, null);
            assertThat(CategoryDiffer.overlayCn(base, sheet, -1)).isSameAs(base);
        }

        @Test
        @DisplayName("buildRowForDepth throws on invalid depth")
        void buildRowForDepthThrows() {
            Category cat = new Category();
            cat.setName("X");
            assertThrows(IllegalArgumentException.class, () ->
                CategoryDiffer.buildRowForDepth(cat, 0) );
        }

        @Test
        @DisplayName("nonBlank returns x if non-blank, fallback otherwise")
        void nonBlankWorks() {
            assertThat(defaultIfNullOrBlank("abc", "fallback")).isEqualTo("abc");
            assertThat(defaultIfNullOrBlank("", "fallback")).isEqualTo("fallback");
            assertThat(defaultIfNullOrBlank(null, "fallback")).isEqualTo("fallback");
        }

        @Test
        @DisplayName("sortByRules sorts DELETE before ADD before UPDATE, and by depth")
        void sortByRulesSortsCorrectly() {
            CategoryChange del = new CategoryChange(DELETE, "A -> B", "B", "b", false, null, null, null, false);
            CategoryChange add = new CategoryChange(ADD, "A", "A", "a", false, null, null, null, false);
            CategoryChange upd = new CategoryChange(UPDATE, "A -> B -> C", "C", "c", false, null, null, null, false);

            var sorted = CategoryDiffer.sortByRules(List.of(upd, add, del));
            assertThat(sorted).extracting(CategoryChange::type)
                    .containsExactly(DELETE, ADD, UPDATE);
        }

        @Test
        @DisplayName("depth returns 0 for blank, correct for others")
        void depthReturnsCorrect() {
            assertThat(CategoryDiffer.depth("")).isEqualTo(0);
            assertThat(CategoryDiffer.depth("A")).isEqualTo(1);
            assertThat(CategoryDiffer.depth("A -> B")).isEqualTo(2);
            assertThat(CategoryDiffer.depth("A -> B -> C")).isEqualTo(3);
        }

        @Test
        @DisplayName("computeDeletes skips nodes with descendants in sheet")
        void computeDeletesSkipsWithDescendants() {
            Map<String, Category> db = Map.of(
                    "A", new Category(),
                    "A -> B", new Category()
            );
            Set<String> sheetKeys = Set.of("A -> B");
            List<CategoryChange> deletes = new CategoryDiffer().computeDeletes(sheetKeys, new HashMap<>(db));
            assertThat(deletes).extracting(CategoryChange::path).doesNotContain("A");
        }

        @Test
        void groupKeyReturnsExpectedValues() {
            CategoryChange del = new CategoryChange(DELETE, "A", "A", "a", false, null, null, null, false);
            CategoryChange add = new CategoryChange(ADD, "B", "B", "b", false, null, null, null, false);
            CategoryChange upd = new CategoryChange(UPDATE, "C", "C", "c", false, null, null, null, false);

            assertThat(CategoryDiffer.groupKey(del)).isEqualTo(0);
            assertThat(CategoryDiffer.groupKey(add)).isEqualTo(1);
            assertThat(CategoryDiffer.groupKey(upd)).isEqualTo(2);
        }

        /* -----------------------------------------------------------------------
         *  MISSING CORNERS
         * ---------------------------------------------------------------------*/
        @Test
        @DisplayName("cmpAddUpd orders parent before child when both are ADDs")
        void addOrderingParentBeforeChild() {
            // DB empty, Excel supplies two rows in “wrong” order (child first).
            ParsedRow leaf  = new ParsedRow("A","", "B","", "C","", false,null,null,null);
            ParsedRow parent= new ParsedRow("A","", "B","", null, null, false,null,null,null);
            List<CategoryChange> changes = differ.diff(List.of(leaf, parent), Map.of());

            // In the diff they must come out parent→child
            assertThat(changes)
                    .filteredOn(ch -> ch.type()==ADD)
                    .extracting(CategoryChange::path)
                    .containsExactly("A", "A -> B", "A -> B -> C");
        }

        @Test
        @DisplayName("Duplicate UPDATE rows are suppressed to a single pair")
        void duplicateUpdateSuppression() {
            // DB has CN="", Excel twice supplies CN="X"
            Category dbCat = makeCategory("Dup"); dbCat.setCnName("");
            ParsedRow sheet = new ParsedRow("Dup","X", null,null,null,null,
                    false, null,null,null);
            List<CategoryChange> changes =
                    differ.diff(List.of(sheet, sheet), Map.of(pathOf(dbCat), dbCat));

            // Only 2 update lines (new+old) — not 4
            assertThat(changes)
                    .filteredOn(ch -> ch.type()==UPDATE)
                    .hasSize(2);
        }

        @Test
        @DisplayName("buildSheetMap keeps the first duplicate row it sees")
        void buildSheetMapResolvesDuplicates() {
            ParsedRow first  = new ParsedRow("Z","1", null,null,null,null, false,null,null,null);
            ParsedRow second = new ParsedRow("Z","2", null,null,null,null, false,null,null,null);
            Map<String, ParsedRow> map =
                    new CategoryDiffer().buildSheetMap(List.of(first, second));

            // merge-function `(a,b)->a` keeps the first
            assertThat(map.get("Z").grandCn()).isEqualTo("1");
        }


        @Test
        @DisplayName("ADD-duplicates: shared ancestor is emitted only once")
        void duplicateAncestorAddSuppressed() {

    /* Excel lists two different leaves under the same grand + parent chain.
       Path “G” would be synthesised twice if not for the `seen` set. */
            ParsedRow leaf1 = new ParsedRow("G","", "P","", "C1","", false,null,null,null);
            ParsedRow leaf2 = new ParsedRow("G","", "P","", "C2","", false,null,null,null);

            List<CategoryChange> changes = differ.diff(List.of(leaf1, leaf2), Map.of());

            // ADD rows expected: 1 for grand, 1 for parent, 1 per leaf  (total = 4)
            assertThat(changes)
                    .filteredOn(ch -> ch.type() == ADD)
                    .extracting(CategoryChange::path)
                    .containsExactly(
                            "G",                       // grand   (only once)
                            "G -> P",                  // parent  (only once)
                            "G -> P -> C1",            // leaf 1
                            "G -> P -> C2"             // leaf 2
                    );
        }


    }  //end of InternalsAndEdgeCases
}
