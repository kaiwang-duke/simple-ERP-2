package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;
import com.nineforce.repository.product.category.CategoryRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-tests for CategoryTableListService")
class CategoryTableListServiceTest {

    @Mock
    private CategoryRepository repo;

    @InjectMocks
    private CategoryTableListService svc;

    @Nested
    @DisplayName("get(int)")
    class GetMethod {
        @Test
        void returnsCategoryWhenFound() {
            Category c = new Category(); c.setId(1);
            when(repo.findById(1)).thenReturn(Optional.of(c));

            Category result = svc.get(1);

            assertSame(c, result);
        }

        @Test
        void throwsWhenNotFound() {
            when(repo.findById(42)).thenReturn(Optional.empty());

            NoSuchElementException ex = assertThrows(
                    NoSuchElementException.class,
                    () -> svc.get(42)
            );
            assertThat(ex.getMessage()).contains("Category 42 not found");
        }
    }

    @Nested
    @DisplayName("save(Category)")
    class SaveMethod {
        @Test
        void delegatesToRepo() {
            Category input = new Category(); input.setName("Foo");
            Category saved = new Category(); saved.setName("Foo"); saved.setId(10);
            when(repo.save(input)).thenReturn(saved);
            Category result = svc.save(input);

            assertSame(saved, result);
            verify(repo).save(input);
        }
    }

    @Nested
    @DisplayName("delete(int)")
    class DeleteMethod {
        @Test
        void deletesById() {
            svc.delete(5);
            verify(repo).deleteById(5);
        }
    }

    @Nested
    @DisplayName("roots()")
    class RootsMethod {
        @Test
        void returnsAllRoots() {
            Category a = new Category(); a.setName("A");
            Category b = new Category(); b.setName("B");
            when(repo.findByParentIsNullOrderByName()).thenReturn(List.of(a, b));

            List<Category> roots = svc.roots();

            assertThat(roots).containsExactly(a, b);
        }
    }

    @Nested
    @DisplayName("getCategoryPath(Long)")
    class GetCategoryPathMethod {
        @Test
        void buildsPathCorrectly() {
            Category root = new Category(); root.setName("Root"); root.setParent(null);
            Category child = new Category(); child.setName("Child"); child.setParent(root);
            when(repo.findByIdWithParent(2L)).thenReturn(Optional.of(child));

            String path = svc.getCategoryPath(2L);

            assertEquals("Root -> Child", path);
        }

        @Test
        @DisplayName("three levels builds full path")
        void threeLevels_buildsFullPath() {
            // arrange a three-level chain
            Category root = new Category(); root.setName("Root"); root.setParent(null);
            Category mid  = new Category(); mid .setName("Mid" ); mid .setParent(root);
            Category leaf = new Category(); leaf.setName("Leaf"); leaf.setParent(mid);

            // stub the repo to return the leaf (which already knows its parent chain)
            when(repo.findByIdWithParent(3L)).thenReturn(Optional.of(leaf));

            // act
            String path = svc.getCategoryPath(3L);

            // assert
            assertEquals("Root -> Mid -> Leaf", path);
        }

        @Test
        void throwsWhenNotFound() {
            when(repo.findByIdWithParent(99L)).thenReturn(Optional.empty());

            assertThrows(
                    NoSuchElementException.class,
                    () -> svc.getCategoryPath(99L)
            );
        }
    }

    @Nested
    @DisplayName("list(String, Pageable)")
    class ListMethod {
        private final Pageable pageable = PageRequest.of(0, 10, Sort.by("name"));

        @Test
        void normalCase_noQuery() {
            Page<Category> page = new PageImpl<>(List.of());
            when(repo.findAll(pageable)).thenReturn(page);

            Page<Category> result = svc.list(null, pageable);

            assertSame(page, result);
        }

        @Test
        void normalCase_withQuery() {
            Page<Category> page = new PageImpl<>(List.of());
            when(repo.findByNameContainingIgnoreCase("q", pageable)).thenReturn(page);

            Page<Category> result = svc.list("q", pageable);

            assertSame(page, result);
        }

        @Test
        @DisplayName("special path sort")
        void specialCase_pathSort() {
            Sort sort = Sort.by(Sort.Order.asc("path"));
            Pageable pageReq = PageRequest.of(0, 2, sort);
            Category c1 = new Category() { @Override public String getPath() { return "B"; } };
            c1.setId(1);
            Category c2 = new Category() { @Override public String getPath() { return "A"; } };
            c2.setId(2);
            when(repo.findAllWithHierarchy()).thenReturn(new ArrayList<>(List.of(c1, c2)));

            Page<Category> result = svc.list("", pageReq);

            assertThat(result.getContent()).extracting(Category::getPath).containsExactly("A", "B");
            assertEquals(2, result.getTotalElements());
        }


        @Test
        @DisplayName("page slice beyond range returns empty")
        void pageSliceBeyondRange() {
            Sort sort = Sort.by(Sort.Order.asc("path"));
            // page 1 (offset 2), i.e. the 3rd element, but we will have 1.
            Pageable pageReq = PageRequest.of(1, 2, sort);
            Category c1 = new Category() {
                @Override
                public String getPath() { return "A"; }
            };
            c1.setId(1);
            when(repo.findAllWithHierarchy()).thenReturn(new ArrayList<>(List.of(c1)));

            Page<Category> result = svc.list("", pageReq);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("special path sort – descending order branch")
        void specialCase_pathSortDescending() {
            Sort sort = Sort.by(Sort.Order.desc("path"));        // ↓ DESC
            Pageable pageReq = PageRequest.of(0, 10, sort);

            Category a = new Category() { @Override public String getPath() { return "A"; } };
            a.setId(1);
            Category b = new Category() { @Override public String getPath() { return "B"; } };
            b.setId(2);
            when(repo.findAllWithHierarchy()).thenReturn(List.of(a, b));

            Page<Category> result = svc.list(null, pageReq);

            // B must come before A because of DESC
            assertThat(result.getContent()).extracting(Category::getPath)
                    .containsExactly("B", "A");
        }

        @Test
        @DisplayName("path-sort with non-blank query delegates to *WithHierarchy(q)*")
        void pathSortWithQueryUsesSearchRepo() {
            Sort sort = Sort.by(Sort.Order.asc("path"));
            Pageable pageReq = PageRequest.of(0, 5, sort);

            // stub both repos so the call returns quickly
            when(repo.findByNameContainingIgnoreCaseWithHierarchy("foo"))
                    .thenReturn(List.of());

            svc.list("foo", pageReq);

            // verify correct repo method was chosen
            verify(repo).findByNameContainingIgnoreCaseWithHierarchy("foo");
            verify(repo, never()).findAllWithHierarchy();
        }

        @Test
        @DisplayName("path-sort page slice hitting middle of list")
        void pathSortMiddleSlice() {
            Sort sort = Sort.by(Sort.Order.asc("path"));
            Pageable pageReq = PageRequest.of(1, 1, sort);   // offset = 1

            Category a = new Category() { @Override public String getPath() { return "Alpha"; } };
            a.setId(1);
            Category b = new Category() { @Override public String getPath() { return "Beta"; } };
            b.setId(2);
            Category c = new Category() { @Override public String getPath() { return "Gamma"; } };
            c.setId(3);
            when(repo.findAllWithHierarchy()).thenReturn(List.of(a, b, c));

            Page<Category> page = svc.list("", pageReq);

            // Should contain *only* the second element (“Beta”)
            assertThat(page.getContent()).extracting(Category::getPath)
                    .containsExactly("Beta");
            assertEquals(3, page.getTotalElements());   // total unaffected
        }


        @Test
        @DisplayName("path-sort DESC with non-blank query")
        void pathSortDescendingWithQuery() {
            Sort sort = Sort.by(Sort.Order.desc("path"));
            Pageable pageReq = PageRequest.of(0, 5, sort);

            Category a = new Category() { @Override public String getPath() { return "A"; } };
            a.setId(1);
            Category b = new Category() { @Override public String getPath() { return "B"; } };
            b.setId(2);

            when(repo.findByNameContainingIgnoreCaseWithHierarchy("foo"))
                    .thenReturn(new ArrayList<>(List.of(a, b)));      // mutable!

            Page<Category> page = svc.list("foo", pageReq);

            // B must appear before A because DESC order is requested
            assertThat(page.getContent()).extracting(Category::getPath)
                    .containsExactly("B", "A");
            verify(repo).findByNameContainingIgnoreCaseWithHierarchy("foo");
        }

        @Test
        @DisplayName("blank query string falls back to findAll")
        void blankQueryFallsBackToFindAll() {
            Pageable pageReq = PageRequest.of(0, 10, Sort.by("name"));
            Page<Category> page = new PageImpl<>(List.of());
            when(repo.findAll(pageReq)).thenReturn(page);

            Page<Category> result = svc.list("   ", pageReq);   // ← q is non-null but blank

            assertSame(page, result);
            verify(repo).findAll(pageReq);                      // ensure correct branch
            verify(repo, never()).findByNameContainingIgnoreCase(any(), any());
        }


    }  // end of ListMethod

    @Nested
    @DisplayName("buildExcel(List<Category>)")
    class BuildExcelMethod {
        @Test
        void createsWorkbookWithCorrectStructure() throws IOException {
            Category c = new Category();
            c.setId(1);
            c.setName("N");
            c.setCnName("C");
            c.setParent(null);
            c.setSeasonal(true);
            c.setPurchaseMonth(3);
            c.setStartSellingMonth(4);
            c.setStopSellingMonth(5);

            when(repo.findByIdWithParent(1L)).thenReturn(Optional.of(c));

            try (XSSFWorkbook wb = svc.buildExcel(List.of(c))) {

                assertEquals(1, wb.getNumberOfSheets());
                assertNotNull(wb.getSheet("Categories"));
                var sheet = wb.getSheetAt(0);
                assertEquals("ID", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("N", sheet.getRow(1).getCell(1).getStringCellValue());

                int purchase_month = (int) sheet.getRow(1).getCell(5).getNumericCellValue();
                assertEquals(3, purchase_month);
            }
        }

        @Test
        @DisplayName("buildExcel for non-seasonal uses default months")
        void createsWorkbookWithDefaultsForNonSeasonal() throws IOException {
            Category c = new Category();
            c.setId(2);
            c.setName("X");
            c.setCnName("Y");
            c.setParent(null);
            c.setSeasonal(false);

            when(repo.findByIdWithParent(2L)).thenReturn(Optional.of(c));

            try (XSSFWorkbook wb = svc.buildExcel(List.of(c))) {
                var sheet = wb.getSheetAt(0);
                // Purchase, start, stop months default to 0
                assertEquals(0.0, sheet.getRow(1).getCell(5).getNumericCellValue(), 0.0);
                assertEquals(0.0, sheet.getRow(1).getCell(6).getNumericCellValue(), 0.0);
            }
        }

        @Test
        @DisplayName("buildExcel writes the parent name when present")
        void buildExcelWritesParentName() throws IOException {
            Category parent = new Category(); parent.setName("P");
            Category child  = new Category();
            child.setId(7);
            child.setName("C");
            child.setParent(parent);
            child.setSeasonal(false);

            when(repo.findByIdWithParent(7L)).thenReturn(Optional.of(child));

            try (XSSFWorkbook wb = svc.buildExcel(List.of(child))) {
                var sheet = wb.getSheetAt(0);

                // Column 4 (index 3) should contain the parent’s name
                assertEquals("P", sheet.getRow(1).getCell(3).getStringCellValue());
            }
        }

    }  //end of BuildExcelMethod
}
