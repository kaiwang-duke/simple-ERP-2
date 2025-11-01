package com.nineforce.model.product.category;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {

    @Test
    void nameSetter_TrimsAndHandlesNullOrBlank() {
        Category c = new Category();

        c.setName("  Foo Bar  ");
        assertEquals("Foo Bar", c.getName(), "should trim surrounding whitespace");

        c.setName("   ");
        assertEquals("", c.getName(), "blank name → empty string");

        c.setName(null);
        assertEquals("", c.getName(), "null name → empty string");
    }

    @Test
    void cnNameSetter_TrimsAndHandlesNullOrBlank() {
        Category c = new Category();

        c.setCnName("  中国  ");
        assertEquals("中国", c.getCnName());

        c.setCnName(" ");
        assertEquals("", c.getCnName(), "blank cnName → empty string");

        c.setCnName(null);
        assertEquals("", c.getCnName(), "null cnName → empty string");
    }

    @Test
    void seasonalFlag_GetterAndSetter() {
        Category c = new Category();
        assertFalse(c.isSeasonal(), "default should be false");

        c.setSeasonal(true);
        assertTrue(c.isSeasonal());
    }

    @Test
    void monthFields_GettersAndSetters() {
        Category c = new Category();
        c.setStartSellingMonth(3);
        c.setStopSellingMonth(11);
        c.setPurchaseMonth(6);

        assertEquals(3, c.getStartSellingMonth());
        assertEquals(11, c.getStopSellingMonth());
        assertEquals(6, c.getPurchaseMonth());
    }

    @Test
    void parentAndChildren_Wiring() {
        Category parent = new Category();
        Category child1 = new Category();
        Category child2 = new Category();

        child1.setParent(parent);
        child2.setParent(parent);

        parent.setChildren(List.of(child1, child2));

        assertSame(parent, child1.getParent());
        assertSame(parent, child2.getParent());
        assertEquals(2, parent.getChildren().size());
    }

    @Test
    void id_GetterAndSetter() {
        Category c = new Category();
        c.setId(42);
        assertEquals(42, c.getId());
    }

    @Test
    void idGetterAndSetter_WorksCorrectly() {
        Category c = new Category();
        c.setId(123);
        assertEquals(123, c.getId());
    }

    @Test
    void getPath_DelegatesToUtility() {
        Category c = new Category();
        // If you want to test this, you'd need to mock CategoryPathUtil
        // So you can just test that it doesn't throw or return null
        assertDoesNotThrow(() -> {
            String path = c.getPath();
            // Optional: assert path is non-null if utility guarantees it
            assertNotNull(path, "Path should not be null");
        });
    }

    @Test
    void setChildren_NullAssignmentAllowed() {
        Category c = new Category();
        c.setChildren(null);
        assertNull(c.getChildren());
    }

    @Test
    void setChildren_AssignmentWorks() {
        Category parent = new Category();
        Category child1 = new Category();
        Category child2 = new Category();
        List<Category> children = List.of(child1, child2);

        parent.setChildren(children);
        assertEquals(2, parent.getChildren().size());
        assertSame(child1, parent.getChildren().get(0));
    }

}
