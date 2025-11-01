package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;
import com.nineforce.repository.product.category.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import static com.nineforce.service.product.category.CategoryTreeListService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-tests for CategoryTreeListService")
class CategoryTreeListServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryTreeListService svc;

    @Nested
    @DisplayName("getCategoryTree()")
    class GetCategoryTreeMethod {
        @Test
        @DisplayName("returns empty map when no top category")
        void emptyWhenNoTop() {
            when(categoryRepository.findByParentIsNull())
                    .thenReturn(Collections.emptyList());

            SortedMap<String, SortedMap<String, SortedSet<String>>> tree = svc.getCategoryTree();
            assertTrue(tree.isEmpty());
        }

        @Test
        @DisplayName("single top-level with no children")
        void singleTopNoChildren() {
            Category top = new Category() {
                @Override public List<Category> getChildren() { return null; }
            };
            top.setName("Cat1");
            top.setCnName("C1");
            when(categoryRepository.findByParentIsNull())
                    .thenReturn(List.of(top));

            var tree = svc.getCategoryTree();
            String key = "Cat1 (C1)";
            assertEquals(1, tree.size());
            assertTrue(tree.containsKey(key));
            assertTrue(tree.get(key).isEmpty());
        }

        @Test
        @DisplayName("two levels and three levels hierarchy")
        void multiLevelHierarchy() {
            Category top = new Category() {
                @Override public List<Category> getChildren() { return List.of(level2()); }
            };
            top.setName("T"); top.setCnName("t");

            when(categoryRepository.findByParentIsNull())
                    .thenReturn(List.of(top));

            var tree = svc.getCategoryTree();
            String topKey = "T (t)";
            String level2Key = "L2 (l2)";
            String level3Val = "L3 (l3)";

            assertThat(tree).containsOnlyKeys(topKey);
            var lvl2Map = tree.get(topKey);
            assertThat(lvl2Map).containsOnlyKeys(level2Key);
            var lvl3Set = lvl2Map.get(level2Key);
            assertThat(lvl3Set).containsExactly(level3Val);
        }

        private Category level2() {
            Category lvl2 = new Category() {
                @Override public List<Category> getChildren() { return List.of(level3()); }
            };
            lvl2.setName("L2"); lvl2.setCnName("l2");
            return lvl2;
        }

        private Category level3() {
            Category lvl3 = new Category();
            lvl3.setName("L3"); lvl3.setCnName("l3");
            return lvl3;
        }

        @Test
        @DisplayName("top category with empty children list")
        void topWithEmptyChildren() {
            // top.getChildren() returns empty list
            Category top = new Category() {
                @Override public List<Category> getChildren() {
                    return Collections.emptyList();
                }
            };
            top.setName("Top");
            top.setCnName("顶");

            when(categoryRepository.findByParentIsNull())
                    .thenReturn(List.of(top));

            var tree = svc.getCategoryTree();
            String key = "Top (顶)";
            assertThat(tree).containsOnlyKeys(key);
            // children map should be empty
            assertThat(tree.get(key)).isEmpty();
        }

        @Test
        @DisplayName("level2 with null children")
        void level2WithNullChildren() {
            // level2.getChildren() returns null
            Category level2 = new Category() {
                @Override public List<Category> getChildren() {
                    return null;
                }
            };
            level2.setName("Mid");
            level2.setCnName("中");

            Category top = new Category() {
                @Override public List<Category> getChildren() {
                    return List.of(level2);
                }
            };
            top.setName("Top");
            top.setCnName("顶");

            when(categoryRepository.findByParentIsNull())
                    .thenReturn(List.of(top));

            var tree = svc.getCategoryTree();
            String topKey = "Top (顶)";
            String lvl2Key = "Mid (中)";
            assertThat(tree).containsOnlyKeys(topKey);
            assertThat(tree.get(topKey)).containsOnlyKeys(lvl2Key);
            // level-3 set should be empty
            assertThat(tree.get(topKey).get(lvl2Key)).isEmpty();
        }

        @Test
        @DisplayName("level2 with empty children list")
        void level2WithEmptyChildrenList() {
            // level2.getChildren() returns empty list
            Category level2 = new Category() {
                @Override public List<Category> getChildren() {
                    return Collections.emptyList();
                }
            };
            level2.setName("Mid");
            level2.setCnName("中");

            Category top = new Category() {
                @Override public List<Category> getChildren() {
                    return List.of(level2);
                }
            };
            top.setName("Top");
            top.setCnName("顶");

            when(categoryRepository.findByParentIsNull())
                    .thenReturn(List.of(top));

            var tree = svc.getCategoryTree();
            String topKey = "Top (顶)";
            String lvl2Key = "Mid (中)";
            assertThat(tree).containsOnlyKeys(topKey);
            assertThat(tree.get(topKey)).containsOnlyKeys(lvl2Key);
            // again, level-3 set should be empty
            assertThat(tree.get(topKey).get(lvl2Key)).isEmpty();
        }
    }   // end of GetCategoryTreeMethod test class





    @Nested
    @DisplayName("buildUnicodeTreeString()")
    class BuildUnicodeTreeStringMethod {
        @Test
        @DisplayName("single branch tree formatting")
        void singleBranchFormatting() {
            // build a map with one top, one level2, one level3
            SortedMap<String, SortedMap<String, SortedSet<String>>> map = new TreeMap<>();
            SortedMap<String, SortedSet<String>> lvl2 = new TreeMap<>();
            SortedSet<String> lvl3 = new TreeSet<>(); lvl3.add("C");
            lvl2.put("B", lvl3);
            map.put("A", lvl2);

            String treeStr = CategoryTreeListService.buildUnicodeTreeString(map);
            String expected = String.join("\n",
                    END_BRANCH + "A",      // only top
                                    INDENT + END_BRANCH + "B",  // only level2
                                        INDENT + INDENT + END_BRANCH + "C", // only level3
                                ""             // ends with newline
            );
            assertEquals(expected, treeStr);
        }

        @Test
        @DisplayName("multiple branches formatting")
        void multipleBranchesFormatting() {
            // two top entries
            SortedMap<String, SortedMap<String, SortedSet<String>>> map = new TreeMap<>();
            // Top1 with one child
            SortedMap<String, SortedSet<String>> lvl2a = new TreeMap<>();
            lvl2a.put("A2", new TreeSet<>());
            map.put("A1", lvl2a);
            // Top2 with no children
            map.put("B1", new TreeMap<>());

            String treeStr = CategoryTreeListService.buildUnicodeTreeString(map);
            String[] lines = treeStr.split("\n");
            // first top: not last => prefix '├── A1'
            // second top: last => prefix    '└── '
            assertTrue(lines[0].startsWith(BRANCH + "A1"));
            assertTrue(lines[2].startsWith(END_BRANCH + "B1"));
        }

        @Test
        @DisplayName("multiple level-3 siblings under one level-2")
        void multipleLevel3Siblings() {
            // Top → one level2 ("B") → two level3 entries ("C1", "C2")
            SortedMap<String, SortedMap<String, SortedSet<String>>> map = new TreeMap<>();
            SortedMap<String, SortedSet<String>> lvl2 = new TreeMap<>();
            SortedSet<String> lvl3 = new TreeSet<>();
            lvl3.add("C1");
            lvl3.add("C2");
            lvl2.put("B", lvl3);
            map.put("A", lvl2);

            String treeStr = CategoryTreeListService.buildUnicodeTreeString(map);
            String[] lines = treeStr.split("\n");

            // line[0] = └── A
            // line[1] =     └── B
            // line[2] =         ├── C1  (first of two level-3: should use '├──')
            // line[3] =         └── C2  (last: '└──')
            assertEquals(END_BRANCH + "A", lines[0]);
            assertEquals(INDENT + END_BRANCH + "B", lines[1]);
            assertTrue(lines[2].startsWith(INDENT + INDENT + BRANCH + "C1"));
            assertTrue(lines[3].startsWith(INDENT + INDENT + END_BRANCH + "C2"));
        }

        @Test
        @DisplayName("multiple level-2 siblings under one top")
        void multipleLevel2Siblings() {
            // Top → two level2 entries:
            //   "B1" with no children
            //   "B2" with a single level-3 ("C")
            SortedMap<String, SortedMap<String, SortedSet<String>>> map = new TreeMap<>();
            Map<String, SortedSet<String>> lvl2a = new TreeMap<>();
            lvl2a.put("B1", new TreeSet<>());            // empty children
            SortedMap<String, SortedSet<String>> lvl2b = new TreeMap<>();
            SortedSet<String> lvl3 = new TreeSet<>(List.of("C"));
            lvl2b.put("B2", lvl3);
            map.put("A", new TreeMap<>(lvl2a));
            map.get("A").putAll(lvl2b);

            String treeStr = CategoryTreeListService.buildUnicodeTreeString(map);
            String[] lines = treeStr.split("\n");

            // line[0] = └── A
            // line[1] =     ├── B1   (first sibling → '├──')
            // line[2] =     └── B2   (last sibling  → '└──')
            // line[3] =         └── C
            assertEquals(END_BRANCH + "A", lines[0]);
            assertEquals(INDENT + BRANCH + "B1", lines[1]);
            assertEquals(INDENT + END_BRANCH + "B2", lines[2]);
            assertEquals(INDENT + INDENT + END_BRANCH + "C", lines[3]);

        }

        @Test
        @DisplayName("level-3 prefix when top is not last")
        void level3PrefixOnNonLastTop() {
            // Top1 has one level2 with two level3 entries → covers isLastTop=false
            SortedMap<String, SortedMap<String, SortedSet<String>>> map = new TreeMap<>();
            SortedMap<String, SortedSet<String>> lvl2 = new TreeMap<>();
            lvl2.put("B", new TreeSet<>(List.of("C1", "C2")));
            map.put("A", lvl2);
            map.put("Z", new TreeMap<>());  // second top, no children

            String[] lines = CategoryTreeListService
                    .buildUnicodeTreeString(map)
                    .split("\n");

            // first level-3 line should start with the “non-last top” prefix
            // prefix = (isLastTop? "    ":"│   ") + (isLastLevel2?"    ":"│   ") + ("├── ")
            // assertTrue(lines[2].startsWith("│       ├── C1"));
            // assertTrue(lines[3].startsWith("│       └── C2"));
            assertTrue(lines[2].startsWith(VERTICAL + INDENT + BRANCH + "C1"));
            assertTrue(lines[3].startsWith(VERTICAL + INDENT + END_BRANCH + "C2"));
        }

        @Test
        @DisplayName("level-3 prefix when level2 is not last")
        void level3PrefixOnNonLastLevel2() {
            // Single top with two level2 entries:
            // - "B1" has one child → hits isLastLevel2=false
            // - "B2" empty
            SortedMap<String, SortedMap<String, SortedSet<String>>> map = new TreeMap<>();
            SortedMap<String, SortedSet<String>> lvl2All = new TreeMap<>();

            lvl2All.put("B1", new TreeSet<>(List.of("C")));  // first level2
            lvl2All.put("B2", new TreeSet<>());              // second level2

            map.put("A", lvl2All);

            String[] lines = CategoryTreeListService
                    .buildUnicodeTreeString(map)
                    .split("\n");

            // line for B1→C should use the "not last level2" path in level-3
            // prefix = (isLastTop? "    ":"│   ") + (isLastLevel2?"    ":"│   ") + ("└── ")
            // here isLastTop=true, isLastLevel2=false, so prefix = "    │   └── "

            assertTrue(lines[2].startsWith(INDENT + VERTICAL + END_BRANCH + "C"));
        }


    }  // end of BuildUnicodeTreeStringMethod test class
}
