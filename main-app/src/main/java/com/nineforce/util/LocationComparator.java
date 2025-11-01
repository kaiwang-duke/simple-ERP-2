package com.nineforce.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

public class LocationComparator implements Comparator<String> {

    final public static Logger logger = LoggerFactory.getLogger(LocationComparator.class);

    /**
     * if S1 > S2, then return 1.  Otherwise, return -1.
     The first part of the location has a few formats
     1) S23-123-5, S1-12-5,
     2) A1-3-5-1, A1-13-5-1
     4) CA[d]-, DA[d]-.
     5) corner cases: room, top, next- etc.

     * @param s1 the first object to be compared.
     * @param s2 the second object to be compared.
     * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
     */
    @Override
    public int compare(String s1, String s2) {
        try {
            String[] parts1 = s1.split("-");
            String[] parts2 = s2.split("-");

            // if the first section isn't the same, then compare string only
            if (parts1[0].charAt(0) != parts2[0].charAt(0)) {
                return s1.compareTo(s2);
            }

            // 1A, 2A, 3B etc. are simple locations. Take it easy.
            // Practical with 52 locations. Not algorithmic correct.
            if (Character.isDigit(parts1[0].charAt(0))) {
                return s1.compareTo(s2);
            }

            // Here are first char same and not a digit
            if (parts1[0].compareTo(parts2[0]) != 0) {
                String digitPart1 = extractDigitPart(parts1[0]);
                String digitPart2 = extractDigitPart(parts2[0]);

                if (digitPart1.length()>0 && digitPart2.length()>0) {
                    int val1 = Integer.parseInt(digitPart1);
                    int val2 = Integer.parseInt(digitPart2);
                    return Integer.compare(val1, val2);
                } else {
                    return s1.compareTo(s2);
                }
            }

            // compare second and later parts. Still can have none digits
            for (int i=1; i<parts1.length && i<parts2.length; i++) {
                // segment differs. Try integer compare then string.
                if (parts1[i].compareTo(parts2[i]) != 0) {
                    String digitPart1 = extractDigitPart(parts1[i]);
                    String digitPart2 = extractDigitPart(parts2[i]);

                    if (digitPart1.length()>0 && digitPart2.length()>0) {
                        int val1 = Integer.parseInt(digitPart1);
                        int val2 = Integer.parseInt(digitPart2);
                        return Integer.compare(val1, val2);
                    } else {  // catch the corner case like room-a, room-b
                        return s1.compareTo(s2);
                    }
                }
            }
        } catch (NumberFormatException  e) {
            logger.error("An exception occurred", e);
            logger.error("LocationComparator: unexpected case {}, {}", s1, s2);
        }

        return s1.compareTo(s2);
    }

    private String extractDigitPart(String s) {
        // Find the index of the first digit in the string
        int i = 0;
        while (i < s.length() && !Character.isDigit(s.charAt(i))) {
            i++;
        }

        // Find the index of the last digit in the string
        int j = i;
        while (j < s.length() && Character.isDigit(s.charAt(j))) {
            j++;
        }

        // Extract the digit part of the string
        return s.substring(i, j);
    }
}
