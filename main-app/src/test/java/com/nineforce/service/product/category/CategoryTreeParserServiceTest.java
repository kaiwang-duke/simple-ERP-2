package com.nineforce.service.product.category;

import com.nineforce.exception.MissingSheetException;
import com.nineforce.model.product.category.Category;
import com.nineforce.repository.product.category.CategoryRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.nineforce.service.product.category.CategoryTreeParserService.ChangeType.*;
import static com.nineforce.service.product.category.CategoryTreeParserService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CategoryTreeParserServiceTest {

    private CategoryRepository repo;
    private CategoryDiffer differ;
    private CategoryTreeParserService service;

    @BeforeEach
    void setUp() {
        repo = mock(CategoryRepository.class);
        differ = mock(CategoryDiffer.class);
        service = new CategoryTreeParserService(repo, differ);
    }

    @Nested
    @DisplayName("parseCategory(Path)")
    class ParseCategoryTests {

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


        @Test
        void normalRowParsing() throws Exception {
            Path temp = Files.createTempFile("test", ".xlsx");
            try (var workbook = new XSSFWorkbook();
                 var out = new FileOutputStream(temp.toFile())) {
                var sheet = workbook.createSheet("product");
                sheet.createRow(0);
                var row = sheet.createRow(1);
                row.createCell(CAT1_EN_CELL).setCellValue("G");
                row.createCell(CAT1_CN_CELL).setCellValue("g_cn");
                row.createCell(CAT2_EN_CELL).setCellValue("P");
                row.createCell(CAT2_CN_CELL).setCellValue("p_cn");
                row.createCell(CAT3_EN_CELL).setCellValue("C");
                row.createCell(CAT3_CN_CELL).setCellValue("c_cn");
                row.createCell(CAT4_IS_SEASONAL_CELL).setCellValue(true);
                row.createCell(CAT5_START_SELLING_MONTH_CELL).setCellValue(5);
                row.createCell(CAT6_END_SELLING_MONTH_CELL).setCellValue(6);
                row.createCell(CAT7_PURCHASE_MONTH_CELL).setCellValue(7);
                workbook.write(out);
            }

            List<CategoryTreeParserService.ParsedRow> rows =
                    CategoryTreeParserService.parseCategory(temp);
            assertThat(rows).hasSize(1);
            var r = rows.get(0);
            assertThat(r.grand()).isEqualTo("G");
            assertThat(r.grandCn()).isEqualTo("g_cn");
            assertThat(r.parent()).isEqualTo("P");
            assertThat(r.parentCn()).isEqualTo("p_cn");
            assertThat(r.child()).isEqualTo("C");
            assertThat(r.childCn()).isEqualTo("c_cn");
            assertThat(r.seasonal()).isTrue();
            assertThat(r.start()).isEqualTo(5);
            assertThat(r.end()).isEqualTo(6);
            assertThat(r.purchase()).isEqualTo(7);
        }

        @Test
        void skipsBlankGrandName() throws Exception {
            Path temp = Files.createTempFile("test_blank", ".xlsx");
            try (var workbook = new XSSFWorkbook();
                 var out = new FileOutputStream(temp.toFile())) {
                var sheet = workbook.createSheet("product");
                sheet.createRow(0);
                var row = sheet.createRow(1);
                row.createCell(CAT1_EN_CELL).setCellValue("");
                workbook.write(out);
            }
            List<CategoryTreeParserService.ParsedRow> rows =
                    CategoryTreeParserService.parseCategory(temp);
            assertThat(rows).isEmpty();
        }

        @Test
        void safeMonthTransformsZeroAndNegative() throws Exception {
            Path temp = Files.createTempFile("test_month", ".xlsx");
            try (var workbook = new XSSFWorkbook();
                 var out = new FileOutputStream(temp.toFile())) {
                var sheet = workbook.createSheet("product");
                sheet.createRow(0);
                var row = sheet.createRow(1);
                row.createCell(CAT1_EN_CELL).setCellValue("X");
                row.createCell(CAT1_CN_CELL).setCellValue("");
                row.createCell(CAT2_EN_CELL).setCellValue("");
                row.createCell(CAT2_CN_CELL).setCellValue("");
                row.createCell(CAT3_EN_CELL).setCellValue("");
                row.createCell(CAT3_CN_CELL).setCellValue("");
                row.createCell(CAT4_IS_SEASONAL_CELL).setCellValue(false);
                row.createCell(CAT5_START_SELLING_MONTH_CELL).setCellValue(0);
                row.createCell(CAT6_END_SELLING_MONTH_CELL).setCellValue(-3);
                row.createCell(CAT7_PURCHASE_MONTH_CELL).setCellValue(1);
                workbook.write(out);
            }
            List<CategoryTreeParserService.ParsedRow> rows =
                    CategoryTreeParserService.parseCategory(temp);
            assertThat(rows).hasSize(1);
            var r = rows.get(0);
            assertThat(r.start()).isEqualTo(1);
            assertThat(r.end()).isEqualTo(1);
            assertThat(r.purchase()).isEqualTo(1);
        }

        @Test
        void throwsMissingSheetException() throws Exception {
            Path temp = Files.createTempFile("test_nosheet", ".xlsx");
            try (var workbook = new XSSFWorkbook();
                 var out = new FileOutputStream(temp.toFile())) {
                workbook.createSheet("other");
                workbook.write(out);
            }
            assertThrows(MissingSheetException.class,
                    () -> CategoryTreeParserService.parseCategory(temp));
        }
   }  // end of ParseCategoryTests


   @Nested
    @DisplayName("previewChanges")
    class PreviewChanges {

        @Test
        void returnsPreviewContextWithDiffAndDbSnapshot() throws Exception {
            // ── arrange ──────────────────────────────────────────────────────────────
            Path dummy = Path.of("dummy.xlsx");

            List<CategoryTreeParserService.ParsedRow> rows = List.of(
                    new ParsedRow("A","A_CN", null,null,null,null,
                            false,null,null,null)
            );
            List<Category> dbCats = List.of(new Category());
            List<CategoryChange> diff = List.of(
                    new CategoryChange(ADD, "A", "A", "A_CN",
                            false,null,null,null,false)
            );

            when(repo.findAllWithHierarchy()).thenReturn(dbCats);
            when(differ.diff(anyList(), anyMap())).thenReturn(diff);

            // 👉 mock the **static** parseCategory
            try (MockedStatic<CategoryTreeParserService> mocked =
                         Mockito.mockStatic(CategoryTreeParserService.class)) {

                mocked.when(() -> CategoryTreeParserService.parseCategory(any(Path.class)))
                        .thenReturn(rows);

                // ── act ───────────────────────────────────────────────────────────────
                var ctx = service.previewChanges(dummy);

                // ── assert ────────────────────────────────────────────────────────────
                assertThat(ctx.changes()).isEqualTo(diff);
                assertThat(ctx.dbMap()).hasSize(1);
                assertThat(ctx.categoryTree()).isNotNull();

                // optional: verify the static was really used
                mocked.verify(() -> CategoryTreeParserService.parseCategory(dummy));
            }
        }

        /* NEW: verifies the list handed to differ() is *exactly* what parse returned */
        @Test void forwardsParsedRowsUntouched() throws Exception {
            ParsedRow excelRow = new ParsedRow("G","", "P","", "C","",
                    false,null,null,null);
            try (MockedStatic<CategoryTreeParserService> mocked =
                         Mockito.mockStatic(CategoryTreeParserService.class)) {

                List<ParsedRow> rows = List.of(excelRow);
                mocked.when(() -> CategoryTreeParserService.parseCategory(any()))
                        .thenReturn(rows);

                when(repo.findAllWithHierarchy()).thenReturn(List.of());
                when(differ.diff(same(rows), anyMap())).thenReturn(List.of());

                service.previewChanges(Path.of("dummy.xlsx"));
                verify(differ).diff(same(rows), anyMap());
            }
        }

       @Test
       void usesTreeBuilderForCategoryTree() throws Exception {
           Path dummy = Files.createTempFile("dummy", ".xlsx");
           List<CategoryTreeParserService.ParsedRow> rows =
                   List.of(new CategoryTreeParserService.ParsedRow("A","A_CN", null,null,null,null,false,null,null,null));
           List<Category> dbCats = List.of(new Category());
           List<CategoryTreeParserService.CategoryChange> diff =
                   List.of(new CategoryTreeParserService.CategoryChange(ADD, "A","A","A_CN",false,null,null,null,false));

           try (MockedStatic<CategoryTreeParserService> parseMock = Mockito.mockStatic(CategoryTreeParserService.class);
                MockedStatic<CategoryTreeListService> treeMock = Mockito.mockStatic(CategoryTreeListService.class)) {

               parseMock.when(() -> CategoryTreeParserService.parseCategory(any(Path.class))).thenReturn(rows);
               when(repo.findAllWithHierarchy()).thenReturn(dbCats);
               when(differ.diff(anyList(), anyMap())).thenReturn(diff);
               treeMock.when(() -> CategoryTreeListService.buildUnicodeTreeString(any())).thenReturn("MY_TREE");

               var ctx = service.previewChanges(dummy);
               assertThat(ctx.categoryTree()).isEqualTo("MY_TREE");
           }
       }

    }  // end of PreviewChanges



    @Nested
    @DisplayName("applyChanges")
    class ApplyChanges {

        @Test
        void appliesAddUpdateDelete() {
            // Arrange
            Category parent = new Category();
            parent.setName("P");
            parent.setId(1);
            Category child = new Category();
            child.setName("C");
            child.setId(2);
            child.setParent(parent);

            Map<String, Category> dbMap = new HashMap<>();
            dbMap.put("P", parent);
            dbMap.put("P -> C", child);

            CategoryChange add = new CategoryChange(ADD, "X", "X", "X_CN", false, null, null, null, false);
            CategoryChange upd = new CategoryChange(UPDATE, "P", "P2", "P2_CN", false, null, null, null, false);
            CategoryChange del = new CategoryChange(DELETE, "P -> C", "C", "C_CN", false, null, null, null, false);

            List<CategoryTreeParserService.CategoryChange> changes = new ArrayList<>(Arrays.asList(add, upd, del));

            // ❷ ───── Mock the repository  ───────────────────────────────────────
            // for add & update – just echo the entity back so nothing is null
            when(repo.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // this stub isn’t used anymore in the current code path but kept for safety
            when(repo.findByIdWithParent(anyLong())).thenReturn(Optional.of(parent));

            // delete() is a void method; doNothing() is the default, but we declare it explicitly
            doNothing().when(repo).delete(any(Category.class));

            // Act
            service.applyChanges(changes, dbMap);

            // Assert
            verify(repo, times(2)).save(any(Category.class));
            verify(repo).delete(child);
        }

        @Test
        void throwsOnUpdateIfCategoryMissing() {
            Map<String, Category> dbMap = new HashMap<>();
            CategoryTreeParserService.CategoryChange upd = new CategoryTreeParserService.CategoryChange(UPDATE, "Z", "Z", "Z_CN", false, null, null, null, false);
            List<CategoryTreeParserService.CategoryChange> changes = new ArrayList<>(List.of(upd));

            assertThrows(RuntimeException.class, () -> service.applyChanges(changes, dbMap));
        }

        @Test
        void throwsOnDeleteIfCategoryMissing() {
            Map<String, Category> dbMap = new HashMap<>();
            CategoryTreeParserService.CategoryChange del = new CategoryTreeParserService.CategoryChange(DELETE, "Z", "Z", "Z_CN", false, null, null, null, false);
            List<CategoryTreeParserService.CategoryChange> changes =  new ArrayList<>(List.of(del));

            assertThrows(RuntimeException.class, () -> service.applyChanges(changes, dbMap));
        }

        @Test
        void appliesNestedAddWithParentAssigned() {
            // Prepare a parent category in dbMap
            Category parent = new Category();
            parent.setName("P"); parent.setId(10);
            Map<String, Category> dbMap = new HashMap<>();
            dbMap.put("P", parent);

            // Change for child addition under parent
            CategoryChange nestedAdd = new CategoryChange(
                    ADD, "P -> C_NEW", "C_NEW", "CN_NEW", false, null, null, null, false);

            // Capture the saved entity
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            when(repo.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            service.applyChanges(List.of(nestedAdd), dbMap);

            verify(repo).save(captor.capture());
            Category saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("C_NEW");
            assertThat(saved.getParent()).isSameAs(parent);
        }


        @Test
        void updateExistingCategoryFieldsAndSave() {
            Category existing = new Category();
            existing.setName("Old"); existing.setCnName("Old_CN");
            existing.setSeasonal(false);
            existing.setStartSellingMonth(1);
            existing.setStopSellingMonth(2);
            existing.setPurchaseMonth(3);
            Map<String,Category> dbMap = new HashMap<>();
            dbMap.put("OldPath", existing);

            CategoryChange update = new CategoryChange(
                    UPDATE, "OldPath", "NewName", "New_CN", true, 5,6,7, false);
            when(repo.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            service.applyChanges(List.of(update), dbMap);

            // verify updated fields
            assertThat(existing.getName()).isEqualTo("NewName");
            assertThat(existing.getCnName()).isEqualTo("New_CN");
            assertThat(existing.isSeasonal()).isTrue();
            assertThat(existing.getStartSellingMonth()).isEqualTo(5);
            assertThat(existing.getStopSellingMonth()).isEqualTo(6);
            assertThat(existing.getPurchaseMonth()).isEqualTo(7);
        }

        @Test
        void appliesAddWithNoParent() {
            var change = new CategoryChange(ADD,"Root","Root","RN",false,1,2,3,false);
            when(repo.save(any())).thenAnswer(inv->inv.getArgument(0));
            Map<String,Category> dbMap = new HashMap<>();
            service.applyChanges(List.of(change),dbMap);
            assertThat(dbMap).containsKey("Root");
        }

        @Test
        void deleteExistingCategory() {
            Category toDel = new Category(); toDel.setName("D");
            Map<String,Category> dbMap = new HashMap<>();
            dbMap.put("D",toDel);
            CategoryChange del = new CategoryChange(DELETE,"D","D","D_CN",false,null,null,null,false);
            service.applyChanges(List.of(del),dbMap);
            verify(repo).delete(toDel);
        }

        @Test
        void nestedAddAssignsParent() {
            Category parent = new Category(); parent.setId(5); parent.setName("P");
            Map<String,Category> dbMap = new HashMap<>(); dbMap.put("P",parent);
            CategoryChange add = new CategoryChange(ADD,"P -> C","C","C_CN",false,1,2,3,false);
            ArgumentCaptor<Category> cap = ArgumentCaptor.forClass(Category.class);
            when(repo.save(any())).thenAnswer(inv->inv.getArgument(0));
            service.applyChanges(List.of(add),dbMap);
            verify(repo).save(cap.capture());
            assertThat(cap.getValue().getParent()).isSameAs(parent);
        }

        @Test
        void updateCallsSave() {
            Category ex = new Category(); ex.setName("X");
            Map<String,Category> dbMap = new HashMap<>(); dbMap.put("X",ex);
            CategoryChange upd = new CategoryChange(UPDATE,"X","Y","Y_CN",true,4,5,6,false);
            when(repo.save(any())).thenAnswer(inv->inv.getArgument(0));
            service.applyChanges(List.of(upd),dbMap);
            assertThat(ex.getName()).isEqualTo("Y");
            verify(repo).save(ex);
        }

        @Test
        void updateSkipsOldLine() {
            Category ex = new Category(); ex.setName("A");
            Map<String,Category> dbMap = new HashMap<>(); dbMap.put("A",ex);
            CategoryChange old = new CategoryChange(UPDATE,"A","Z","Z_CN",false,1,2,3,true);
            service.applyChanges(List.of(old),dbMap);
            verify(repo,never()).save(any());
        }

        @Test
        void deleteMissingThrows() {
            assertThrows(RuntimeException.class,
                    () -> service.applyChanges(List.of(
                            new CategoryChange(DELETE,"X","X","",false,null,null,null,false)
                    ),new HashMap<>()));
        }
    }   // and of ApplyChanges

    @Test
    void buildCategoryTreeBuildsExpectedStructure() {
        var row = new CategoryTreeParserService.ParsedRow("A", "A_CN", "B", "B_CN", "C", "C_CN", false, null, null, null);
        List<CategoryTreeParserService.ParsedRow> rows = List.of(row);

        var tree = service.buildCategoryTree(rows);

        assertThat(tree).containsKey("A (A_CN)");
        assertThat(tree.get("A (A_CN)")).containsKey("B (B_CN)");
        assertThat(tree.get("A (A_CN)").get("B (B_CN)")).contains("C (C_CN)");
    }



    @Nested
    @DisplayName("ParsedRow helpers")
    class ParsedRowTests {

        @Test
        void parsedRowLoggingOnMismatchAtDebug() {
            // set logger to DEBUG and attach a ListAppender
            ch.qos.logback.classic.Logger log =
                    (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CategoryTreeParserService.class);
            ch.qos.logback.classic.Level prevLevel = log.getLevel();
            log.setLevel(ch.qos.logback.classic.Level.DEBUG);

            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
            listAppender.start();
            log.addAppender(listAppender);

            // create a ParsedRow and a non-matching Category
            ParsedRow row = new ParsedRow("G","g_CN","P","p_CN","C","c_CN", true, 1,2,3);
            Category mismatch = new Category();
            mismatch.setName("X"); mismatch.setCnName("Y_cn");
            mismatch.setSeasonal(false);
            mismatch.setStartSellingMonth(9);
            mismatch.setStopSellingMonth(9);
            mismatch.setPurchaseMonth(9);

            boolean same = row.isSameAs(mismatch);
            assertThat(same).isFalse();

            // verify that a debug log was emitted containing the diff header
            List<ch.qos.logback.classic.spi.ILoggingEvent> logs = listAppender.list;
            assertThat(logs).isNotEmpty();
            String msg = logs.get(0).getFormattedMessage();
            assertThat(msg).contains("≠  DIFF at [G -> P -> C]");

            // cleanup logger
            log.detachAppender(listAppender);
            log.setLevel(prevLevel);
        }

        // existing ParsedRow helper tests unchanged
    }


    /* --------------------------------------------------------------------------
     *  EXTRA COVERAGE  – previously untouched branches
     * ------------------------------------------------------------------------ */
    @Nested
    @DisplayName("More edge cases for 100 % coverage")
    class ExtraBranches {

        /* ---------- ParsedRow helpers & comparisons ------------------------ */
        @Test
        @DisplayName("ParsedRow.levelOf / enAtLevel / cnAtLevel work for all 3 depths")
        void parsedRowHelpersAllDepths() {
            // depth-1
            ParsedRow d1 = new ParsedRow("G","g", null,null,null,null,
                    false,null,null,null);
            assertThat(d1.levelOf()).isEqualTo(1);
            assertThat(d1.enAtLevel(1)).isEqualTo("G");
            assertThat(d1.cnAtLevel(1)).isEqualTo("g");

            // depth-2
            ParsedRow d2 = new ParsedRow("G","g", "P","p", null,null,
                    false,null,null,null);
            assertThat(d2.levelOf()).isEqualTo(2);
            assertThat(d2.enAtLevel(2)).isEqualTo("P");
            assertThat(d2.cnAtLevel(2)).isEqualTo("p");

            // depth-3
            ParsedRow d3 = new ParsedRow("G","g", "P","p", "C","c",
                    true,1,2,3);
            assertThat(d3.levelOf()).isEqualTo(3);
            assertThat(d3.enAtLevel(3)).isEqualTo("C");
            assertThat(d3.cnAtLevel(3)).isEqualTo("c");

            // isSameAs – exact match ⇢ true, field diff ⇢ false
            Category match = new Category();                    // mirror of d3
            match.setName("C");          match.setCnName("c");
            match.setSeasonal(true);     match.setStartSellingMonth(1);
            match.setStopSellingMonth(2);match.setPurchaseMonth(3);
            Category mis   = new Category(); mis.setName("zzz");
            assertThat(d3.isSameAs(match)).isTrue();
            assertThat(d3.isSameAs(mis)).isFalse();
        }

        /* ---------- safeMonth – all three branches ------------------------ */
        @Test
        void safeMonthReturnsExpectedValue() throws Exception {
            var m = CategoryTreeParserService.class.getDeclaredMethod(
                    "safeMonth", Integer.class);
            m.setAccessible(true);
            assertThat(m.invoke(null, (Integer) null)).isEqualTo(1);  // null branch
            assertThat(m.invoke(null, 0)).isEqualTo(1);               // ≤0 branch
            assertThat(m.invoke(null, 7)).isEqualTo(7);               // passthrough
        }

        /* ---------- applyChanges: ADD with blank leaf name is skipped ------ */
        @Test
        void addWithBlankNameIsSilentlyIgnored() {
            CategoryChange blankAdd = new CategoryChange(
                    ADD, "Q", "", "", false, null, null, null, false);

            service.applyChanges(
                    new ArrayList<>(List.of(blankAdd)), new HashMap<>());

            verify(repo, never()).save(any());
        }

        /* ---------- applyChanges: repo.save throws → wrapped RuntimeException */
        @Test
        void duplicateCategoryWrapsIntegrityViolation() {
            // ── temporarily mute only this test ────────────────────────────────
            ch.qos.logback.classic.Logger log =
                    (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                            CategoryTreeParserService.class);          // or full package string

            ch.qos.logback.classic.Level prev = log.getLevel();    // remember
            log.setLevel(ch.qos.logback.classic.Level.OFF);        // mute

            try {
                CategoryChange add = new CategoryChange(
                        ADD, "X", "X", "", false, null, null, null, false);

                // make repo.save throw the DataIntegrityViolationException
                when(repo.save(any(Category.class)))
                        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

                var ex = assertThrows(RuntimeException.class,
                        () -> service.applyChanges(
                                new ArrayList<>(List.of(add)), new HashMap<>()));

                assertThat(ex).hasMessageContaining("Duplicate category detected");
            }
            finally {
                // ── restore the original log level ─────────────────────────────
                log.setLevel(prev);
            }
        }

        /* ---------- buildCategoryTree: grand-only row (no parent/child) ---- */
        @Test
        void buildTreeWithSingleRootNoChildren() {
            ParsedRow onlyRoot = new ParsedRow("Solo", "根", null,null,null,null,
                    false,null,null,null);

            SortedMap<String, SortedMap<String, SortedSet<String>>> tree =
                    service.buildCategoryTree(List.of(onlyRoot));

            assertThat(tree).containsKey("Solo (根)");
            assertThat(tree.get("Solo (根)")).isEmpty();          // no level-2 map
        }

    }  // end of ExtraBranches




}