package com.nineforce.controller.product.category;

import com.nineforce.service.product.category.CategoryTreeListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class CategoryTreeListControllerTest {

    @Mock
    private CategoryTreeListService treeSvc;

    // controller is created with constructor injection →
    @InjectMocks
    private CategoryTreeListController controller;

    private SortedMap<String, SortedMap<String, SortedSet<String>>> sampleTree;

    @BeforeEach
    void setUp() {
        /* sample tree:
             Electronics
                └─ Phones
                     └─ iPhone
         */
        sampleTree = new TreeMap<>();
        SortedMap<String, SortedSet<String>> electronics = new TreeMap<>();
        electronics.put("Phones", new TreeSet<>(java.util.List.of("iPhone")));
        sampleTree.put("Electronics", electronics);

        //when(treeSvc.getCategoryTree()).thenReturn(sampleTree);
        doReturn(sampleTree).when(treeSvc).getCategoryTree();
    }

    @Test
    void showCategoryTree_populatesModelAndReturnsView() {
        // given
        Model model = new ExtendedModelMap();

        // when
        String viewName = controller.showCategoryTree(model);

        String expectedUnicode =
                CategoryTreeListService.buildUnicodeTreeString(sampleTree);

        // then
        assertThat(viewName).isEqualTo("products/categories/category-tree-list");
        assertThat(model.getAttribute("categoryTree")).isEqualTo(sampleTree);
        assertThat(model.getAttribute("unicodeTreeString")).isEqualTo(expectedUnicode);
        assertThat(model.getAttribute("title")).isEqualTo("Inventory File Converter");
    }
}
