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

import java.util.Arrays;
import java.util.Objects;


/**
 * Entries consist of Kanji elements, reading elements, general information and sense elements.
 * Each entry must have at least one reading element and one sense element. Others are optional.
 * <p>
 * <b>Implementation note:</b> a large number of instances of this class may exist in the JVM,
 * so its internal representation have to be compact.
 *
 * @author Martin Desruisseaux
 */
public final class Entry {
    /**
     * A unique numeric sequence number for each entry.
     *
     * @see ElementType#ent_seq
     */
    public final int identifier;

    /**
     * A word or short phrase in Japanese which is written using at least one non-kana character.
     * The Kanji element, or in its absence, the reading element, is the defining component of each
     * entry. The overwhelming majority of entries will have a single Kanji element associated with
     * a word in Japanese.
     * <p>
     * If non-null, the class is either a single {@link String} or {@code String[]}. We do not use
     * unconditionally an array because only a minority of cases (less than 10%) have two or more
     * words. This field is null in 16% of cases and a single {@link String} in 57% of cases, so
     * it is worth to avoid the cost of array creation.
     *
     * @see ElementType#keb
     */
    private Object kanji;

    /**
     * Valid readings of the word(s) in the Kanji element using modern kanadzukai. Where there are
     * multiple reading elements, they will typically be alternative readings of the Kanji element.
     * <p>
     * If non-null, the type is either a single {@link String} or {@code String[]}. We do not use
     * unconditionally an array because only a minority of cases (about 6%) have two or more words,
     * so it is worth to avoid the cost of array creation.
     *
     * @see ElementType#reb
     */
    private Object reading;

    /**
     * The Kanji or reading element priority ID. The first {@code getCount(true)} values are
     * Kanji priorities, and the remainder are reading priorities. We merge those priorities
     * in a single array because there is only about 500 different pairs of (Kanji, reading)
     * priority, so we can share the same arrays between many {@code Entry} instance.
     *
     * @see ElementType#ke_pri
     * @see ElementType#re_pri
     */
    private short[] priorities;

    /**
     * Creates an initially empty entry.
     *
     * @param ent_seq A unique numeric sequence number for each entry.
     */
    Entry(final int ent_seq) {
        this.identifier = ent_seq;
    }

    /**
     * Adds the given information to this entry.
     *
     * @param isKanji  {@code true} for adding the Kanji element, or {@code false} for the reading element.
     * @param word     The Kanji or reading element to add. Can not be {@code null}.
     * @param priority The priority of the word being added, or 0 if none.
     */
    final void add(final boolean isKanji, final String word, final short priority) {
        Objects.requireNonNull(word);
        Object words = isKanji ? kanji : reading;
        final int count; // Number of Kanji or reading elements after this method.
        if (words == null) {
            words = word;
            count = 1;
        } else if (words instanceof String) {
            words = new String[] {(String) words, word};
            count = 2;
        } else {
            String[] array = (String[]) words;
            count = array.length + 1;
            array = Arrays.copyOf(array, count);
            array[count-1] = word;
            words = array;
        }
        if (isKanji) this.kanji   = words;
        else         this.reading = words;
        /*
         * At this point, the Kanji or reading elements field has been updated.
         * Now update the priority field if a priority has been specified. See
         * the 'priority' field javadoc for explanation about the packing.
         */
        if (priority != 0) {
            assert count == getCount(words) : count;
            int i1 = count; // (index where to insert) + 1.
            if (!isKanji) {
                i1 += getCount(kanji); // Reading elements are after Kanjis.
            }
            short[] priorities = this.priorities;
            if (priorities == null) {
                priorities = new short[i1];
            } else {
                int length = priorities.length;
                if (isKanji) {
                    final int countRe = length - (count - 1);
                    if (countRe > 0) {
                        priorities = Arrays.copyOf(priorities, ++length);
                        System.arraycopy(priorities, count-1, priorities, count, countRe);
                    }
                }
                if (length < i1) {
                    priorities = Arrays.copyOf(priorities, i1);
                }
            }
            priorities[i1-1] = priority;
            this.priorities = priorities;
        }
        assert count == getCount(isKanji) : count;
        assert word.equals(getWord(isKanji, count-1)) : count;
        assert priority == getPriority(isKanji, count-1) : count;
    }

    /**
     * Returns the number of Kanji or reading elements.
     *
     * @param  kanji {@code true} for the Kanji elements, or {@code false} for the reading elements.
     * @return Number of Kanji or reading elements.
     */
    public int getCount(final boolean kanji) {
        return getCount(kanji ? this.kanji : reading);
    }

    /**
     * Implementation of {@link #getCount(boolean)} used when field to query is known in advance.
     *
     * @param value The value of the {@link #kanji} or {@link #reading} field.
     * @return Number of Kanji or reading elements.
     */
    private static int getCount(final Object value) {
        if (value == null) {
            return 0;
        } else if (value instanceof String[]) {
            return ((String[]) value).length;
        } else {
            return 1;
        }
    }

    /**
     * Returns the defining element, which is the Kanji if present or the reading element otherwise.
     *
     * @param  index The index of the element for which to get the defining word.
     * @return The Kanji or reading elements, or {@code null} if the index is out of bounds.
     */
    public String getDefiningWord(final int index) {
        final String kanji = getWord(true, index);
        return (kanji != null) ? kanji : getWord(false, index);
    }

    /**
     * Returns the Kanji or reading elements at the given index, or {@code null} if none.
     * This method does not thrown an exception for non-negative index out of bounds.
     *
     * @param  kanji {@code true} for the Kanji element, or {@code false} for the reading element.
     * @param  index The index of the element for which to get the word.
     * @return The Kanji or reading elements, or {@code null}.
     *
     * @see ElementType#keb
     * @see ElementType#reb
     */
    public String getWord(final boolean kanji, final int index) {
        final Object value = kanji ? this.kanji : reading;
        if (value != null) {
            if (value instanceof String) {
                if (index == 0) {
                    return (String) value;
                }
            } else {
                final String[] words = (String[]) value;
                if (index < words.length) {
                    return words[index];
                }
            }
        }
        return null;
    }

    /**
     * Returns the priority of the Kanji or reading elements, at the given index or {@code 0}
     * if none. This method does not thrown an exception for non-negative index out of bounds,
     * because priorities are optional.
     *
     * @param  kanji {@code true} for the Kanji element, or {@code false} for the reading element.
     * @param  index The index of the element for which to get the priority.
     * @return The priority of the Kanji or reading elements, or {@code 0} if none.
     *
     * @see ElementType#ke_pri
     * @see ElementType#re_pri
     */
    public short getPriority(final boolean kanji, int index) {
        final short[] priorities = this.priorities;
        if (priorities != null) {
            if (!kanji) {
                index += getCount(this.kanji);
            }
            if (index < priorities.length) {
                return priorities[index];
            }
        }
        return 0;
    }

    /**
     * Sorts the Kanji or reading elements by priority. This method should be invoked only
     * after all elements have been {@linkplain #add(boolean, String, short) added} to this
     * entry. We use the "bubble" sort method, which is simple but slow. However since we
     * should have very few entries (only a single entry in about 90% of cases), the slow
     * behavior is not an issue.
     */
    final void sortByPriority(final boolean kanji) {
        final Object value = kanji ? this.kanji : reading;
        if (value instanceof String[]) {
            final String[] words = (String[]) value;
            final short[] priorities = this.priorities;
            final int offset = kanji ? 0 : getCount(this.kanji);
            boolean modified;
            do {
                modified = false;
                for (int i=1; i<words.length; i++) {
                    final short p1 = priorities[i+offset-1];
                    final short p2 = priorities[i+offset  ];
                    // The 0 value stands for "not classified",
                    // (NULL in the database), which we sort last.
                    if (p2 != 0 && (p1 == 0 || p1 > p2)) {
                        priorities[i+offset-1] = p2;
                        priorities[i+offset  ] = p1;
                        final String s1 = words[i-1];
                        words[i-1] = words[i];
                        words[i] = s1;
                        modified = true;
                    }
                }
            } while (modified);
        }
    }
}
