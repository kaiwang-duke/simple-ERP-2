package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;
import com.nineforce.repository.product.category.CategoryRepository;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class CategoryTreeListService {

    static final String BRANCH = "├── ";
    static final String END_BRANCH = "└── ";
    static final String VERTICAL = "│   ";
    static final String INDENT = "    ";

    private final CategoryRepository categoryRepository;
    private static final Logger logger = LoggerFactory.getLogger(CategoryTreeListService.class);


    public CategoryTreeListService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;   // ← injected once, immutable
    }

    /**
     * Builds a nested category tree from the database.
     * The structure is:
     *   Cat1 (name (cnName)) -> { Cat2 (name (cnName)) -> { Cat3 (name (cnName)), ... } }
     */
    @Transactional
    public SortedMap<String, SortedMap<String, SortedSet<String>>> getCategoryTree() {
        SortedMap<String, SortedMap<String, SortedSet<String>>> categoryTree = new TreeMap<>();
        // Retrieve top-level category (parent is null)
        List<Category> topCategories = categoryRepository.findByParentIsNull();
        for (Category top : topCategories) {
            logger.trace("Top category: {} ({}) has {} children.",top.getName(), top.getCnName(),
                         top.getChildren() != null ? top.getChildren().size() : 0 );

            // Combine English and Chinese names for display.
            String cat1Key = top.getName() + " (" + top.getCnName() + ")";
            SortedMap<String, SortedSet<String>> level2Map = new TreeMap<>();

            if (top.getChildren() != null) {
                for (Category level2 : top.getChildren()) {
                    logger.trace("  Level2 category: {} ({}) has {} children.", level2.getName(), level2.getCnName(),
                            level2.getChildren() != null ? level2.getChildren().size() : 0);

                    String cat2Key = level2.getName() + " (" + level2.getCnName() + ")";
                    SortedSet<String> level3Set = new TreeSet<>();

                    if (level2.getChildren() != null) {
                        for (Category level3 : level2.getChildren()) {
                            logger.trace( "    Level3 category: {} ({})", level3.getName(), level3.getCnName());
                            String cat3Value = level3.getName() + " (" + level3.getCnName() + ")";
                            level3Set.add(cat3Value);
                        }
                    }
                    level2Map.put(cat2Key, level3Set);
                }
            }
            categoryTree.put(cat1Key, level2Map);
        }
        logger.trace(buildUnicodeTreeString(categoryTree));
        return categoryTree;
    }


    /**
     * Builds a Unicode tree string from a nested category map.
     * The map structure is:
     *   TopLevel -> ( SecondLevel -> Set of ThirdLevel )
     *
     * @param categoryTree the nested category map
     * @return a string representing the tree in a Unicode format
     */
    public static String buildUnicodeTreeString(SortedMap<String, SortedMap<String, SortedSet<String>>> categoryTree) {
        StringBuilder sb = new StringBuilder();
        int topCount = 0;
        int totalTops = categoryTree.size();

        // Loop through top-level entries
        for (SortedMap.Entry<String, SortedMap<String, SortedSet<String>>> topEntry : categoryTree.entrySet()) {
            topCount++;
            boolean isLastTop = (topCount == totalTops);
            String topPrefix = isLastTop ? END_BRANCH : BRANCH;
            sb.append(topPrefix).append(topEntry.getKey()).append("\n");

            SortedMap<String, SortedSet<String>> level2Map = topEntry.getValue();
            int level2Count = 0;
            int totalLevel2 = level2Map.size();

            // Loop through second-level entries
            for (SortedMap.Entry<String, SortedSet<String>> level2Entry : level2Map.entrySet()) {
                level2Count++;
                boolean isLastLevel2 = (level2Count == totalLevel2);
                // Prefix for level 2: if top is last then just indent, otherwise vertical bar remains.
                String level2Prefix = (isLastTop ? INDENT : VERTICAL) + (isLastLevel2 ? END_BRANCH : BRANCH);
                sb.append(level2Prefix).append(level2Entry.getKey()).append("\n");

                Set<String> level3Set = level2Entry.getValue();
                int level3Count = 0;
                int totalLevel3 = level3Set.size();
                // Loop through third-level entries
                for (String level3Val : level3Set) {
                    level3Count++;
                    boolean isLastLevel3 = (level3Count == totalLevel3);
                    // For level 3, add an extra indent
                    String level3Prefix = (isLastTop   ? INDENT   : VERTICAL)
                            + (isLastLevel2 ? INDENT   : VERTICAL)
                            + (isLastLevel3 ? END_BRANCH : BRANCH);
                    sb.append(level3Prefix).append(level3Val).append("\n");
                }
            }
        }
        return sb.toString();
    }
}
