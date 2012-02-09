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

import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;
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
public final class Entry implements Comparable<Entry> {
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
     * The meaning of this entry. This field is either a {@link Sense} instance if the meaning is
     * available only for the first locale, or a {@code Sense[]} array if the meaning is available
     * for more locales or if there is many meanings.
     */
    private Object senses;

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
        if (priority != 0 || isKanji) {
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
     * @param  isKanji {@code true} for the Kanji elements, or {@code false} for the reading elements.
     * @return Number of Kanji or reading elements.
     */
    public int getCount(final boolean isKanji) {
        return getCount(isKanji ? kanji : reading);
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
     * @param  isKanji {@code true} for the Kanji element, or {@code false} for the reading element.
     * @param  index The index of the element for which to get the word.
     * @return The Kanji or reading elements, or {@code null}.
     *
     * @see ElementType#keb
     * @see ElementType#reb
     */
    public String getWord(final boolean isKanji, final int index) {
        final Object value = isKanji ? kanji : reading;
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
     * @param  isKanji {@code true} for the Kanji element, or {@code false} for the reading element.
     * @param  index The index of the element for which to get the priority.
     * @return The priority of the Kanji or reading elements, or {@code 0} if none.
     *
     * @see ElementType#ke_pri
     * @see ElementType#re_pri
     */
    public short getPriority(final boolean isKanji, int index) {
        final short[] priorities = this.priorities;
        if (priorities != null) {
            final int nk = getCount(kanji);
            if (!isKanji) {
                index += nk; // Reading elements are stored after Kanjis.
            } else if (index >= nk) {
                return 0; // Kanji index is actually in the reading elements part.
            }
            if (index < priorities.length) {
                return priorities[index];
            }
        }
        return 0;
    }

    /**
     * Returns a single priority value used for sorting entries.
     */
    private short getPriority() {
        short priority = Short.MAX_VALUE;
        if (priorities != null) {
            for (final short p : priorities) {
                if (p != 0 && p < priority) {
                    priority = p;
                }
            }
        }
        return priority;
    }

    /**
     * Returns the length of the shortest word. This is used for sorting entries.
     */
    private int getSmallestLength(final boolean isKanji) {
        int length = Short.MAX_VALUE;
        for (int i=getCount(isKanji); --i>=0;) {
            final String word = getWord(isKanji, i);
            if (word != null) {
                final int c = word.codePointCount(0, word.length());
                if (c < length) length = c;
            }
        }
        return length;
    }

    /**
     * Adds a sense to the list of existing senses.
     */
    final void addSense(final Sense sense) {
        if (senses == null) {
            senses = sense;
        } else if (senses instanceof Sense) {
            senses = new Sense[] {(Sense) senses, sense};
        } else {
            Sense[] array = (Sense[]) senses;
            final int length = array.length;
            array = Arrays.copyOf(array, length+1);
            array[length] = sense;
            senses = array;
        }
    }

    /**
     * If there is more than one sense, create a new sense which summarize all existing senses.
     *
     * @param locales The locales for which to create summaries.
     */
    final void addSenseSummary(final Locale[] locales) {
        if (senses instanceof Sense[]) {
            Sense[] array = (Sense[]) senses;
            for (final Locale locale : locales) {
                final Sense summary = Sense.summarize(locale, array);
                if (summary != null) {
                    final int length = array.length;
                    array = new Sense[length + 1];
                    array[0] = summary;
                    System.arraycopy(senses, 0, array, 1, length);
                    senses = array;
                }
            }
            // Sort in user language order preference.
            Arrays.sort(array, new Comparator<Sense>() {
                @Override public int compare(final Sense o1, final Sense o2) {
                    return o2.indexOf(locales) - o1.indexOf(locales);
                }
            });
        }
    }

    /**
     * Returns the meaning of this entry as a comma-separated list of senses
     * in the given language.
     *
     * @param  locale The language for the meaning, or {@code null} for the first
     *         available language in preference order.
     * @return The meaning of this entry, or {@code null} if none.
     */
    public Sense getSense(final Locale locale) {
        final Object senses = this.senses; // Protect from changes.
        if (senses != null) {
            if (senses instanceof Sense[]) {
                for (final Sense candidate : (Sense[]) senses) {
                    if (locale == null || locale.equals(candidate.locale)) {
                        return candidate;
                    }
                }
            } else {
                final Sense candidate = (Sense) senses;
                if (locale == null || locale.equals(candidate.locale)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Compares this entry with the given object in order to sort preferred entries first.
     * If two entry has the same priority, select the shortest word.
     *
     * @param other The other entry.
     * @return -1 if this entry has priority over the given entry, +1 for the converse.
     */
    @Override
    public int compareTo(final Entry other) {
        int c = getPriority() - other.getPriority();
        if (c == 0) {
            c = getSmallestLength(true) - other.getSmallestLength(true);
            if (c == 0) {
                c = getSmallestLength(false) - other.getSmallestLength(false);
            }
        }
        return c;
    }
}
