package com.nineforce.controller.product.category;

import com.nineforce.service.product.category.CategoryTreeParserService;
import com.nineforce.util.FirebaseAuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CategoryTreeParserController.class)
@AutoConfigureMockMvc
@WithMockUser
class CategoryTreeParserControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired @MockitoBean
    private CategoryTreeParserService parserSvc;

    @Autowired @MockitoBean
    private FirebaseAuthUtil firebaseAuthUtil;

    /* shared dummies --------------------------------------------------------- */
    private CategoryTreeParserService.CategoryPreviewContext emptyPreview;
    private CategoryTreeParserService.CategoryPreviewContext diffPreview;

    private final Path dummyPath = Path.of("dummy.xlsx");

    @BeforeEach
    void setUp() {
        Mockito.reset(parserSvc, firebaseAuthUtil);

        when(firebaseAuthUtil.getUserEmail()).thenReturn("tester@example.com");

        /* -------- preview #1  :  NO DIFFERENCES ----------------------------- */
        emptyPreview = mock(CategoryTreeParserService.CategoryPreviewContext.class);
        when(emptyPreview.changes()).thenReturn(Collections.emptyList());
        when(emptyPreview.categoryTree()).thenReturn("└── (empty)");
        when(emptyPreview.dbMap()).thenReturn(Map.of());

        /* -------- preview #2  :  ONE DIFFERENCE ----------------------------- */
        CategoryTreeParserService.CategoryChange change =
                mock(CategoryTreeParserService.CategoryChange.class);

        diffPreview = mock(CategoryTreeParserService.CategoryPreviewContext.class);
        when(diffPreview.changes()).thenReturn(List.of(change));
        when(diffPreview.categoryTree()).thenReturn("└── Electronics …");
        when(diffPreview.dbMap()).thenReturn(Map.of());
    }

    /* ─────────────────────────────────────────────────────────────────────── */

    @Test
    void index_ShouldRenderUploadForm() throws Exception {
        mockMvc.perform(get("/products/categories/category-tree-parser"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/categories/category-tree-parser"))
                .andExpect(model().attribute("userEmail", "tester@example.com"))
                .andExpect(model().attribute("title", "Category Tree Parser"));
    }

    @Test
    void process_NoChanges_ShouldReturnSameView() throws Exception {
        when(parserSvc.previewChanges(any())).thenReturn(emptyPreview);

        MockMultipartFile file = new MockMultipartFile(
                "file", "cats.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/products/categories/category-tree-parser/process")
                        .file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("products/categories/category-tree-parser"))
                .andExpect(model().attribute("categoryTree", "└── (empty)"))
                .andExpect(model().attribute("userEmail", "tester@example.com"))
                .andExpect(model().attribute("title", "Category Tree Parser"));

        verify(parserSvc).previewChanges(any());
        verifyNoMoreInteractions(parserSvc);
    }

    @Test
    void process_WithChanges_ShouldRenderReviewPage() throws Exception {
        when(parserSvc.previewChanges(any())).thenReturn(diffPreview);

        MockMultipartFile file = new MockMultipartFile(
                "file", "cats.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{4, 5, 6});

        mockMvc.perform(multipart("/products/categories/category-tree-parser/process")
                        .file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("products/categories/category-tree-review"))
                .andExpect(model().attribute("changes", diffPreview.changes()))
                .andExpect(model().attribute("categoryTree", "└── Electronics …"))
                .andExpect(model().attribute("userEmail", "tester@example.com"))
                .andExpect(model().attribute("title", "Category Tree Differences"));

        verify(parserSvc).previewChanges(any());
    }

    @Test
    void apply_WithPendingChanges_ShouldInvokeServiceAndRedirect() throws Exception {
        // 1) start a session
        var session = mockMvc.perform(get("/products/categories/category-tree-parser"))
                .andReturn()
                .getRequest()
                .getSession();

        List<CategoryTreeParserService.CategoryChange> changes = diffPreview.changes();


        assertNotNull(session, "Session should have been created by the GET request");
        session.setAttribute("pendingChanges", changes);
        session.setAttribute("pendingDbMap", diffPreview.dbMap());
        session.setAttribute("pendingFile", dummyPath);

        // 2) hit /apply
        mockMvc.perform(post("/products/categories/category-tree-parser/apply")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/categories/category-tree-parser"));

        verify(parserSvc).applyChanges(changes, diffPreview.dbMap());
    }
}
