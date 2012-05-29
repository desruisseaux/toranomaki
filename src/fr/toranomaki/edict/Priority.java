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

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Collections;


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
        news((short) 2, (short) 6, (short) 3),

        /**
        * Appears in the "<cite>Ichimango goi bunruishuu</cite>", Senmon Kyouiku Publishing,
        * Tokyo, 1998. (The entries marked "{@code ichi2}" were demoted from "{@code ichi1}"
        * because they were observed to have low frequencies in the WWW and newspapers.)
        */
        ichi((short) 2, (short) 4, (short) 3),

        /**
        * Small number of words use this marker when they are detected as
        * being common, but are not included in other lists.
        */
        spec((short) 2, (short) 2, (short) 3),

        /**
        * Common loanwords, based on the "<cite>wordfreq</cite>" file.
        */
        gai((short) 2, (short) 0, (short) 3),

        /**
        * Indicator of frequency-of-use ranking in the "<cite>wordfreq</cite>" file. The numeric
        * value is the number of the set of 500 words in which the entry can be found, with {@code 01}
        * assigned to the first 500, {@code 02} to the second, and so on.
        */
        nf((short) 49, (short) 8, (short) 0xFFFF);

        /**
         * The maximal allowed rank value.
         */
        final short max;

        /**
         * A shift to apply to the {@link Priority#rank} value in order to get the numerical
         * code to store in the binary file.
         */
        private final short shift;

        /**
         * The mask to apply on the code in order to get back the rank.
         */
        private final short mask;

        /**
         * Creates a new enumeration.
         */
        private Type(final short max, final short shift, final short mask) {
            this.max   = max;
            this.shift = shift;
            this.mask  = mask;
        }

        /**
         * The weight of the given rank when computing the numerical code.
         *
         * @param  rank The rank given to this priority type, or {@code null} if none.
         * @return The value to add to the priority primary key.
         */
        public final int rankToCode(final Short rank) {
            return ((rank != null) ? rank : max+1) << shift;
        }

        /**
         * The rank encoded in the given code for this type, or {@code null} if none.
         * This method is the converse of {@link #rankToCode(Short)}.
         *
         * @param  code The code.
         * @return The rank extracted from the given code, or {@code null} if none.
         */
        public final Short codeToRank(int code) {
            code = (code >>> shift) & mask;
            return (code != 0 && code != max+1) ? Short.valueOf((short) code) : null;
        }
    }

    /**
     * A cache of priority instances. We don't put a limit to this cache because the
     * maximal amount of such instances is reasonably low (less than 400).
     */
    private static final Map<Priority,Priority> CACHE = new HashMap<>();

    /**
     * A cache of priority sets. We don't put a limit to this cache because the
     * maximal amount of such instances is reasonably low (less than 400).
     */
    private static final Map<Short,Set<Priority>> SET_CACHE = new HashMap<>();

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
     * Creates a new priority of the given type and rank.
     *
     * @param type Codes indicating the reference.
     * @param rank The rank of this priority.
     */
    public Priority(final Type type, final short rank) {
        this.type = type;
        this.rank = rank;
        ensureValid();
    }

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
                ensureValid();
                return;
            }
        }
        throw new IllegalArgumentException("Unparsable priority: " + name);
    }

    /**
     * Returns an instance equals to this priority, from the cache if such instance
     * has already been created before this method call.
     *
     * @return The priority of the given type and rank.
     */
    private Priority intern() {
        assert Thread.holdsLock(Priority.class);
        final Priority existing = CACHE.get(this);
        if (existing != null) {
            return existing;
        }
        CACHE.put(this, this);
        return this;
    }

    /**
     * Returns the set of priority from the given code.
     *
     * @param  code The code from which to get the set of priorities.
     * @return The set of priorities from the given code.
     */
    public static synchronized Set<Priority> fromCode(final short code) {
        if (code == 0) {
            return Collections.emptySet();
        }
        final Short key = code;
        Set<Priority> priorities = SET_CACHE.get(key);
        if (priorities == null) {
            priorities = new LinkedHashSet<>(4);
            for (final Priority.Type type : Priority.Type.values()) {
                final Short rank = type.codeToRank(code & 0xFFFF);
                if (rank != null) {
                    priorities.add(new Priority(type, rank).intern());
                }
            }
            SET_CACHE.put(key, priorities);
        }
        return priorities;
    }

    /**
     * Invoked by the constructors to ensure that the {@link #rank} value is valid.
     */
    private void ensureValid() {
        if (rank < 1 || rank > type.max) {
            throw new IllegalArgumentException("Rank " + rank + " is out of range for type \"" + type + "\".");
        }
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
