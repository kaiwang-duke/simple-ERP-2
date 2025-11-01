package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.nineforce.util.StringUtil.toTitle;
import static com.nineforce.util.StringUtil.trimToEmpty;

import static com.nineforce.service.product.category.CategoryPathUtil.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests for {@link CategoryPathUtil}.
 *  ▸  Covers both overloads of {@code pathOf}.
 *  ▸  Verifies the public separator constants behave as documented.
 *  ▸  Exercises {@code normalize} for null / blank / trimming.
 */
class CategoryPathUtilTest {

    /* --------------------------------------------------------------------- */
    /* Helper that builds a tiny tree on demand                              */
    /* --------------------------------------------------------------------- */
    private static Category cat(String name, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setParent(parent);
        return c;
    }

    /* ========================== entity overload ========================== */
    @Nested
    @DisplayName("pathOf(Category)")
    class EntityOverload {

        @Test
        void oneLevel() {
            Category root = cat("Root", null);
            assertEquals("Root", pathOf(root));
        }

        @Test
        void pathOfEntity_onlyRoot() {
            Category root = new Category();
            root.setName("  mixed   CaSe ");
            // no parent
            assertThat(pathOf(root)).isEqualTo("Mixed Case");
        }


        @Test
        void twoLevels() {
            Category child = cat("Child", cat("Root", null));
            assertEquals("Root -> Child", pathOf(child));
        }

        @Test
        void threeLevels() {
            Category leaf = cat("Leaf", cat("Child", cat("Root", null)));
            assertEquals("Root -> Child -> Leaf", pathOf(leaf));
        }

        @Test
        @DisplayName("pathOf(null) throws IAE")
        void pathOf_nullThrows() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> pathOf(null),
                    "Category cannot be null"
            );
        }

        @Test
        void pathOfEntity_nullOrBlankName() {
            Category c = new Category();
            c.setName(null);
            assertThat(pathOf(c)).isEqualTo("");        // you’ll end up with one empty part
        }

    }

    /* =========================== String overload ========================= */
    @Nested
    @DisplayName("pathOf(String,String,String)")
    class PathOfStringOverload {

        @ParameterizedTest(name = "[{index}] ({0},{1},{2}) → \"{3}\"")
        @CsvSource(textBlock = """
            Grand,Parent,Self,Grand -> Parent -> Self
            Grand,Parent,,   Grand -> Parent
            Grand,,Self,      Grand -> Self
            Grand,, ,         Grand
            Grand,,  ,        Grand
        """)
        void buildsExpectedPath(String l1, String l2, String l3, String expected) {
            assertEquals(expected, pathOf(l1, l2, l3));
        }

        @Test
        void ignoreLeadingTrailingSpaces() {
            String path = pathOf("  Grand  ", " Parent ", "  Self  ");
            assertEquals("Grand -> Parent -> Self", path);
        }

        @Test
        void pathOfStrings_allBlank() {
            assertThat(pathOf(null, " ", "")).isEmpty();
        }
        @Test
        void pathOfStrings_skipEmptyMiddle() {
            assertThat(pathOf("Root", "  ", "leaf"))
                    .isEqualTo("Root -> Leaf");
        }
    }

    /* ====================== public constants must stay =================== */
    @Test
    void separatorAndRegexStayInSync() {
        String built = String.join(CategoryPathUtil.SEP, List.of("A", "B", "C"));
        String[] split = built.split(CategoryPathUtil.SEP_REGEX);
        assertArrayEquals(new String[]{"A", "B", "C"}, split,
                "If this assertion ever fails someone changed the constants!");
    }

    /* ============================ normalize ============================== */
    @ParameterizedTest
    @CsvSource(textBlock = """
        '  abc  ',   abc
        "",          ""
        '   ',      ''
        ,            ''
    """)
    void normalizeBehaves(String in, String expected) {
        assertEquals(expected, trimToEmpty(in));
    }

    /* ========================= edge-case safety ========================== */
    @Test
    void nullCategoryThrowsNpe() {
        //noinspection ConstantConditions  // <-- IntelliJ will skip the next line
        Executable call = () -> pathOf(null);
        assertThrows(IllegalArgumentException.class, call,
                "Null should fail fast – adapt assertion if policy changes.");
    }


    /* ========================== prefixPaths helper ========================= */
    @Nested
    @DisplayName("prefixPaths(String) – via reflection")
    class PrefixPaths {

        /** Convenience wrapper to call the private static method */
        @SuppressWarnings("unchecked")
        private List<String> call(String fullPath) throws Exception {
            var m = CategoryPathUtil.class
                    .getDeclaredMethod("prefixPaths", String.class);
            m.setAccessible(true);
            return (List<String>) m.invoke(null, fullPath);
        }

        @Test
        void oneLevel_hasNoPrefixes() throws Exception {
            assertTrue(call("Root").isEmpty());
        }

        @Test
        void twoLevels_returnsRootOnly() throws Exception {
            assertEquals(
                    List.of("Root"),
                    call("Root -> Child"));
        }

        @Test
        void threeLevels_returnsRootThenRootChild() throws Exception {
            assertEquals(
                    List.of("Root", "Root -> Child"),
                    call("Root -> Child -> Leaf"));
        }

        @Test
        void trimsAndHonoursCustomSeparator() {
            // build the crazy-spaced path with the real separator
            String sep = CategoryPathUtil.SEP;               // e.g. " → "
            String path = " A  " + sep + "  B  " + sep + "   C ";

            // call helper
            List<String> actual = prefixPaths(path);

            // expected prefixes, also composed with the real separator
            List<String> expected = List.of(
                    "A",
                    "A" + sep + "B"
            );
            assertEquals(expected, actual);
        }

        @Test
        void prefixPaths_varyingWhitespace() {
            List<String> p = prefixPaths(" A  ->   B ->C ");
            assertIterableEquals(
                    List.of("A", "A -> B"),
                    p,
                    "prefixPaths should split and trim correctly"
            );
        }

    }

    @Test
    void toTitle_nullOrBlank() {
        // use reflection or make toTitle package‐private
        assertThat(toTitle(null)).isEmpty();
        assertThat(toTitle("   ")).isEmpty();
    }
}
