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

import java.util.Collections;
import java.util.Locale;
import java.util.Set;


/**
 * The sense of an {@link Entry}. Each entry may have many senses.
 *
 * @author Martin Desruisseaux
 */
public class Sense {
    /**
     * The locale for this sense.
     */
    public final Locale locale;

    /**
     * Target-language words or phrases which are equivalents to the Japanese word.
     *
     * @see ElementType#gloss
     */
    public final String meaning;

    /**
     * The <cite>Part Of Speech</cite> (POS) for this sense. This set shall not be modified,
     * since many senses will share the same set. We do not wraps the set in an unmodifiable
     * view because some {@code EnumSet} method implementations are more efficient when their
     * argument is another {@code EnumSet}.
     *
     * @see ElementType#pos
     */
    public final Set<PartOfSpeech> partOfSpeech;

    /**
     * Creates a new sense.
     */
    Sense(final Locale locale, final String meaning, final Set<PartOfSpeech> partOfSpeech) {
        this.locale       = locale;
        this.meaning      = meaning;
        this.partOfSpeech = partOfSpeech;
    }

    /**
     * Returns the index of {@link #locale} in the given array, or -1 if none. This
     * method is used for sorting sense instances in user language preference order.
     */
    final int indexOf(final Locale[] locales) {
        int i = locales.length;
        while (--i >= 0) {
            if (locale.equals(locales[i])) break;
        }
        return i;
    }

    /**
     * Creates a new sense which summarize all other senses.
     *
     * @param  locale The locale for this sense.
     * @param  senses The other senses to summarize.
     * @return The summarized sense, or {@code null} if not needed.
     */
    static Sense summarize(final Locale locale, final Sense[] senses) {
        String            first   = null;
        StringBuilder     buffer  = null;
        Set<PartOfSpeech> largest = Collections.emptySet();
        for (final Sense candidate : senses) {
            if (locale.equals(candidate.locale)) {
                if (candidate.partOfSpeech.contains(largest)) {
                    largest = candidate.partOfSpeech;
                }
                if (first == null) {
                    first = candidate.meaning;
                } else {
                    if (buffer == null) {
                        buffer = new StringBuilder(first);
                    }
                    buffer.append(", ").append(candidate.meaning);
                }
            }
        }
        return (buffer != null) ? new Summary(locale, buffer.toString(), largest) : null;
    }

    /**
     * The instance which represent a summary of other senses.
     */
    private static final class Summary extends Sense {
        Summary(final Locale locale, final String meaning, final Set<PartOfSpeech> partOfSpeech) {
            super(locale, meaning, partOfSpeech);
        }

        @Override
        public boolean isSummary() {
            return true;
        }
    }

    /**
     * Returns {@code true} if this {@code Sense} instances is actually a summary of other senses.
     * Summary are represented by a comma-separated list of all individual senses of the same
     * locale.
     *
     * @return {@code true} if this sense is a summary of other senses.
     */
    public boolean isSummary() {
        return false;
    }

    /**
     * Returns a string representation of the {@link #partOfSpeech} value.
     *
     * @return A string representation of the part of speech, or {@code null} if none.
     */
    public final String getPartOfSpeech() {
        String        first  = null;
        StringBuilder buffer = null;
        for (final PartOfSpeech pos : partOfSpeech) {
            if (first == null) {
                first = pos.toString();
            } else {
                if (buffer == null) {
                    buffer = new StringBuilder(first);
                }
                buffer.append(", ").append(pos);
            }
        }
        return (buffer != null) ? buffer.toString() : first;
    }

    /**
     * Returns a string representation of this sense for debugging purpose.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + locale.getISO3Language() + ": " + meaning + ']';
    }
}
