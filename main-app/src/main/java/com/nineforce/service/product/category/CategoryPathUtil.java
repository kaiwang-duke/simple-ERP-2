package com.nineforce.service.product.category;

import com.nineforce.model.product.category.Category;

import java.util.*;
import java.util.regex.Pattern;
import static com.nineforce.util.StringUtil.toTitle;
import static com.nineforce.util.StringUtil.trimToEmpty;

public final class CategoryPathUtil {
    private CategoryPathUtil() {}

    /** Literal separator shown in the UI */
    public static final String SEP = " -> ";      // pick any symbol you like

    /** Regex version – safe for String.split / Pattern matching */
    public static final String SEP_REGEX = "\\s*" + Pattern.quote(SEP.trim()) + "\\s*";

    /* ------------------------------------------------------------------
     * 1) Entity overload – walks up the tree, so it works for any depth
     * ------------------------------------------------------------------ */
    public static String pathOf(Category c) {
        if (c == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        List<String> parts = new ArrayList<>();
        Category cur = c;
        while (cur != null) {
            parts.add(toTitle(cur.getName()));
            cur = cur.getParent();         // assumes parent is already loaded
        }
        Collections.reverse(parts);
        return String.join(SEP, parts);
    }

    /* ------------------------------------------------------------------
     * 2) String overload – use when you only have raw names from Excel
     *    root      = mandatory      (top-level category, e.g. "Tools")
     *    child     = optional       (second level, e.g. "Hand Tools")
     *    leaf      = optional       (third level, e.g. "Wrenches")
     * ------------------------------------------------------------------ */

    public static String pathOf(String grand, String parent, String self) {
        List<String> parts = new ArrayList<>(3);
        if (!trimToEmpty(grand ).isEmpty()) parts.add(toTitle(grand ));
        if (!trimToEmpty(parent).isEmpty()) parts.add(toTitle(parent));
        if (!trimToEmpty(self  ).isEmpty()) parts.add(toTitle(self )); // only when L3 present
        return String.join(SEP, parts);
    }

    /** Returns all ancestor prefixes of a path, *excluding* the path itself. */
    public static List<String> prefixPaths(String fullPath) {
        String[] parts = fullPath.split(CategoryPathUtil.SEP_REGEX);
        List<String> out = new ArrayList<>(parts.length - 1);

        String acc = "";
        for (int i = 0; i < parts.length - 1; i++) {        // ← skip leaf
            acc = acc.isEmpty() ? parts[i] : acc + CategoryPathUtil.SEP + parts[i];
            out.add(acc.trim());
        }
        return out;
    }

    /** Returns a path with the given prefix, or an empty string if the prefix is blank. */
    public static String bracketedCnName(String s) {
        return (s == null || s.isBlank()) ? "" : " (" + s.trim() + ")";
    }
}
