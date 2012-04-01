/*
 *    Toranomaki - Help with Japanese words using the EDICT dictionary.
 *    (C) 2012, Martin Desruisseaux
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 3 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *    Lesser General Public License for more details.
 */
package fr.toranomaki.edict;

import java.util.Comparator;


/**
 * A partially case-insensitive comparator for strings.
 * This comparator defines an order like "AaBbCc" rather than "ABCabc".
 *
 * @author Martin desruisseaux
 */
final class WordComparator implements Comparator<String> {
    /**
     * The unique instance.
     */
    static final Comparator<String> INSTANCE = new WordComparator();

    /**
     * Do not allows other instances than {@link #INSTANCE}.
     */
    private WordComparator() {
    }

    /**
     * Compares the given strings as indicated in the class-javadoc.
     */
    @Override
    public int compare(final String s1, final String s2) {
        int i1 = 0;
        int i2 = 0;
        final int n1 = s1.length();
        final int n2 = s2.length();
        while (i1 < n1 && i2 < n2) {
            final int c1 = s1.codePointAt(i1);
            final int c2 = s2.codePointAt(i2);
            if (c1 != c2) {
                int m1 = Character.toUpperCase(c1);
                int m2 = Character.toUpperCase(c2);
                if (m1 != m2) {
                    m1 = Character.toLowerCase(c1);
                    m2 = Character.toLowerCase(c2);
                    if (m1 != m2) {
                        return m1 - m2;
                    }
                }
                return c1 - c2;
            }
            i1 += Character.charCount(c1);
            i2 += Character.charCount(c2);
        }
        return n1 - n2;
    }
}
