package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;
import com.nineforce.repository.product.category.CategoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoryTreeDBPrinter {

    private final CategoryRepository repo;

    public CategoryTreeDBPrinter(CategoryRepository repo) {
        this.repo = repo;
    }

    /**
     * Builds the same table you had in SQL, but returns it as one big String.
     * You can then render it in a <pre> block in your MVC view.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public String buildTreeString() {
        StringBuilder sb = new StringBuilder();

        // header
        sb.append(String.format("%-6s | %-38s | %-5s | %-15s | %s%n",
                "id", "category_tree", "level", "path_numeric", "path_text"));
        sb.append("------+-")
                .append("--------------------------------------+")
                .append("-------+")
                .append("-----------------+")
                .append("-------------------------")
                .append(System.lineSeparator());

        // fetch roots
        List<Category> roots = repo.findByParentIsNullOrderByName();
        for (Category root : roots) {
            appendNode(root,
                    /*indent*/    "",
                    /*level*/     1,
                    /*pathNum*/   String.valueOf(root.getId()),
                    /*pathText*/  root.getName(),
                    sb);
        }

        return sb.toString();
    }

    private void appendNode(Category cat,
                            String indent,
                            int level,
                            String pathNum,
                            String pathText,
                            StringBuilder sb) {
        // build formatted name
        String formattedName = (level == 1
                ? cat.getName()
                : indent + "└── " + cat.getName());

        // append one row
        sb.append(String.format("%-6d | %-38s | %-5d | %-15s | %s%n",
                cat.getId(),
                formattedName,
                level,
                pathNum,
                pathText));

        // recurse into children
        String childIndent = indent + "    ";
        for (Category child : cat.getChildren()) {
            appendNode(child,
                    childIndent,
                    level + 1,
                    pathNum + "." + child.getId(),
                    pathText + " -> " + child.getName(),
                    sb);
        }
    }
}

