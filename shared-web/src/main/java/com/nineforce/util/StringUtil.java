package com.nineforce.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class StringUtil {

    // ---------------------------------------------------------------------
    // “default” helpers
    // ---------------------------------------------------------------------
    /**
     * Returns {@code value} if it is neither {@code null} nor blank;
     * otherwise returns the given {@code fallback}.
     * <p>
     * A <i>blank</i> string is one whose {@code length() == 0} after
     * trimming, or whose characters are all Unicode whitespace.
     *
     * <pre>{@code
     * defaultWhenNullOrBlank("Alice", "Bob")   → "Alice"
     * defaultWhenNullOrBlank("   ",   "Bob")   → "Bob"
     * defaultWhenNullOrBlank(null,   "Bob")    → "Bob"
     * }</pre>
     *
     * @param x    the string to test (may be {@code null})
     * @param fallback the value to return when {@code value} is {@code null}
     *                 or {@linkplain String#isBlank() blank}
     * @return {@code value} if non-blank; otherwise {@code fallback}
     */
    public static String defaultIfNullOrBlank(String x, String fallback) {
        return (x != null && !x.isBlank()) ? x : fallback;
    }



    // ---------------------------------------------------------------------
    // Case helpers
    // ---------------------------------------------------------------------

    /**
     * Produces a simple <em>Title Case</em> version of {@code text}.
     * <ul>
     *   <li>Trims leading/trailing whitespace</li>
     *   <li>Collapses multiple internal spaces to a single space</li>
     *   <li>Lower-cases the remainder of each word</li>
     *   <li>Upper-cases the first character of each word</li>
     * </ul>
     *
     * <pre>{@code
     * StringUtil.toTitle("  aLiCe  in  WONDERLAND ")
     * // → "Alice In Wonderland"
     * }</pre>
     *
     * @param s free-form input (may be {@code null})
     *
     * @return a title-cased string, or an empty string when {@code text} is
     *         {@code null}
     */
    public static String toTitle(String s) {
        if (s == null) return "";
        return Arrays.stream(s.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(w -> !w.isBlank())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }


    /**
     * Replaces {@code null} or all-whitespace input with {@code ""} and trims
     * anything else.
     *
     * <pre>{@code
     * StringUtil.trimToEmpty(null)        // → ""
     * StringUtil.trimToEmpty("   ")       // → ""
     * StringUtil.trimToEmpty("  Foo ")    // → "Foo"
     * }</pre>
     *
     * @param s the input string (may be {@code null})
     * @return a non-{@code null} string: either the trimmed input or {@code ""}
     */
    public static String trimToEmpty(String s) {
        return (s == null || s.isBlank()) ? "" : s.trim();
    }


}
