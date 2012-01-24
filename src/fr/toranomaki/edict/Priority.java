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


/**
 * Provides record information about the relative priority of an entry. Priority is intended for
 * use either by applications which want to concentrate on entries of a particular priority, or
 * to generate subset files.
 * <p>
 * The reason both the Kanji and reading elements are tagged is because on occasions a priority
 * is only associated with a particular Kanji/reading pair.
 *
 * @author Martin Desruisseaux
 *
 * @see ElementType#ke_pri
 * @see ElementType#re_pri
 */
public final class Priority implements Comparable<Priority> {
   /**
    * Codes indicating the reference taken as an indication of the frequency with which
    * the word is used.
    *
    * <p><b>Credit:</b>This enumeration javadoc is a slightly edited copy-and-paste of the EDICT
    * documentation included in the {@code JMdict.xml} file.</p>
    *
    * @author Martin Desruisseaux
    */
    public enum Type {
        /**
        * Appears in the "<cite>wordfreq</cite>" file compiled by Alexandre Girardi from the
        * <cite>Mainichi Shimbun</cite>. (See the Monash ftp archive for a copy.) Words in the
        * first 12,000 in that file are marked "{@code news1}" and words in the second 12,000
        * are marked "{@code news2}"
        */
        news((short) 2, (short) 27),

        /**
        * Appears in the "<cite>Ichimango goi bunruishuu</cite>", Senmon Kyouiku Publishing,
        * Tokyo, 1998. (The entries marked "{@code ichi2}" were demoted from "{@code ichi1}"
        * because they were observed to have low frequencies in the WWW and newspapers.)
        */
        ichi((short) 2, (short) 9),

        /**
        * Small number of words use this marker when they are detected as
        * being common, but are not included in other lists.
        */
        spec((short) 2, (short) 3),

        /**
        * Common loanwords, based on the "<cite>wordfreq</cite>" file.
        */
        gai((short) 2, (short) 1),

        /**
        * Indicator of frequency-of-use ranking in the "<cite>wordfreq</cite>" file. The numeric
        * value is the number of the set of 500 words in which the entry can be found, with {@code 01}
        * assigned to the first 500, {@code 02} to the second, and so on.
        */
        nf((short) 49, (short) 81);

        /**
         * The maximal allowed rank value.
         */
        final short max;

        /**
         * A factor by which to multiply the {@link Priority#rank} value in order to get a
         * primary key to use in the database. Actually this computation is not strictly
         * necessary, but we use it for having a better sorting.
         */
        private final short factor;

        /**
         * Creates a new enumeration.
         */
        private Type(final short max, final short factor) {
            this.max = max;
            this.factor = factor;
        }

        /**
         * The weight of the given rank when computing the primary key value.
         */
        final int weight(final Short rank) {
            return ((rank != null) ? rank-1 : max) * factor;
        }
    }

    /**
     * Codes indicating the reference taken as an indication of the frequency with which
     * the word is used.
     */
    public final Type type;

    /**
     * The rank of this priority. For types {@link Type#news new}, {@link Type#ichi ichi},
     * {@link Type#spec spec} and {@link Type#gai gai}, the rank value can be only 1 or 2.
     * For type {@link Type#nf nf}, it can be any value in the range 1 to 50.
     */
    public final short rank;

    /**
     * Creates a new priority from the given name.
     *
     * @param  name The priority name to parse.
     * @throws IllegalArgumentException If the enum type has no constant with the specified name.
     * @throws NumberFormatException If the digits are not parseable.
     */
    public Priority(String name) throws IllegalArgumentException, NumberFormatException {
        name = name.trim();
        for (int i=name.length(); --i>=0;) {
            final char c = name.charAt(i);
            if (c < '0' || c > '9') {
                type = Type.valueOf(name.substring(0, ++i));
                rank = Short.parseShort(name.substring(i));
                if (rank < 1 || rank > type.max) {
                    throw new IllegalArgumentException("Rank " + rank + " is out of range for type \"" + type + "\".");
                }
                return;
            }
        }
        throw new IllegalArgumentException("Unparsable priority: " + name);
    }

    /**
     * Returns the string representation of this priority.
     */
    @Override
    public String toString() {
        return type.name() + rank;
    }

    /**
     * Returns the hash code value for this priority.
     */
    @Override
    public int hashCode() {
        return type.hashCode() + rank;
    }

    /**
     * Compares this priority with the given object for equality.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Priority) {
            final Priority other = (Priority) obj;
            return type == other.type && rank == other.rank;
        }
        return false;
    }

    /**
     * Compares this priority with the given instance for order.
     */
    @Override
    public int compareTo(final Priority o) {
        int r = type.compareTo(o.type);
        if (r == 0) {
            r = rank - o.rank;
        }
        return r;
    }
}
