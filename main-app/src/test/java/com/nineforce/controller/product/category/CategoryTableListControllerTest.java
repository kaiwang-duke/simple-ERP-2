package com.nineforce.controller.product.category;

import com.nineforce.model.product.category.Category;
import com.nineforce.service.product.category.CategoryTableListService;
import com.nineforce.util.FirebaseAuthUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;


import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(controllers = CategoryTableListController.class)
//@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureMockMvc
@WithMockUser       // import org.springframework.security.test.context.support.WithMockUser;

class CategoryTableListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @MockitoBean(name = "categoryTableListService")   // ← bean name matches @… in the template
    private CategoryTableListService categoryTableListService;

    @Autowired
    @MockitoBean
    private FirebaseAuthUtil firebaseAuthUtil;

    private Category sampleCat;

    @BeforeEach
    void setUp() {
        Mockito.reset(categoryTableListService, firebaseAuthUtil);

        sampleCat = new Category();
        sampleCat.setId(42);
        sampleCat.setName("Foo");
        sampleCat.setCnName("吧");
        sampleCat.setSeasonal(true);
        sampleCat.setPurchaseMonth(5);
        sampleCat.setStartSellingMonth(3);
        sampleCat.setStopSellingMonth(10);

        when(firebaseAuthUtil.getUserEmail()).thenReturn("user@example.com");
        when(categoryTableListService.getCategoryPath(anyLong())).thenReturn("dummy-path"); // stub used by template
    }

    @Test
    void list_NoQueryParam_ShouldReturnPageAndView() throws Exception {
        Page<Category> page = new PageImpl<>(List.of(sampleCat), PageRequest.of(0, 10), 1);
        when(categoryTableListService.list(eq(""), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/products/categories"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/categories/category-table-list"))
                .andExpect(model().attribute("page", page))
                .andExpect(model().attribute("q", ""))
                .andExpect(model().attribute("userEmail", "user@example.com"))
                .andExpect(model().attribute("title", "Category Table List"));

        verify(categoryTableListService).list(eq(""), any(Pageable.class));
    }

    @Test
    void list_WithQueryParam_ShouldPassThrough() throws Exception {
        Page<Category> page = new PageImpl<>(List.of(sampleCat));
        when(categoryTableListService.list(eq("foo"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/products/categories")
                        .param("q", "foo")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("q", "foo"));

        verify(categoryTableListService).list(eq("foo"), any(Pageable.class));
    }

    @Test
    void form_New_ShouldProvideEmptyCategoryAndRoots() throws Exception {
        when(categoryTableListService.roots()).thenReturn(List.of(sampleCat));

        mockMvc.perform(get("/products/categories/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/categories/category-form"))
                .andExpect(model().attributeExists("category"))
                .andExpect(model().attribute("roots", List.of(sampleCat)))
                .andExpect(model().attribute("userEmail", "user@example.com"));
    }

    @Test
    void form_Edit_ShouldPopulateExistingCategory() throws Exception {
        when(categoryTableListService.get(42)).thenReturn(sampleCat);
        when(categoryTableListService.roots()).thenReturn(List.of(sampleCat));

        mockMvc.perform(get("/products/categories/42/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/categories/category-form"))
                .andExpect(model().attribute("category", sampleCat))
                .andExpect(model().attribute("roots", List.of(sampleCat)))
                .andExpect(model().attribute("userEmail", "user@example.com"));
    }

    @Test
    void save_ShouldRedirectAndFlashDetails() throws Exception {
        when(categoryTableListService.save(any(Category.class))).thenReturn(sampleCat);

        mockMvc.perform(post("/products/categories/save")
                        .param("name", "New")
                        .with(csrf())  )          // ← add CSRF token
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/categories"))
                .andExpect(flash().attribute("msg", "Saved!"))
                .andExpect(flash().attributeExists("details"));

        verify(categoryTableListService).save(any(Category.class));
    }

    @Test
    void delete_ShouldRedirectAndFlashMsg() throws Exception {
        mockMvc.perform( post("/products/categories/42/delete")
                .with(csrf())            // ← add CSRF token
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/categories"))
                .andExpect(flash().attribute("msg", "Deleted!"));

        verify(categoryTableListService).delete(42);
    }

    @Test
    void export_ShouldReturnExcelFile() throws Exception {
        Page<Category> page = new PageImpl<>(List.of(sampleCat));
        when(categoryTableListService.list(eq(""), any(Pageable.class))).thenReturn(page);

        // extract the content list into a local variable
        List<Category> expectedContent = page.getContent();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("test");
            when(categoryTableListService.buildExcel(eq(expectedContent))).thenReturn(wb);

            MockHttpServletResponse resp = mockMvc.perform(get("/products/categories/export")
                            .param("q", "")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=category.xlsx"))
                    .andReturn()
                    .getResponse();

            assertThat(resp.getContentAsByteArray().length).isGreaterThan(0);

            //noinspection resource
            verify(categoryTableListService).buildExcel(expectedContent);   // optional sanity check
        }
    }
}
