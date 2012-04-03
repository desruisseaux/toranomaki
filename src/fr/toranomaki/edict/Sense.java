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
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import fr.toranomaki.grammar.GrammaticalClass;


/**
 * The sense of an {@link Entry}. Each entry may have many senses.
 *
 * @author Martin Desruisseaux
 */
public class Sense {
    /**
     * The locale for the Russian language.
     */
    public static final Locale RUSSIAN = new Locale("ru");

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
     *
     * @param locale  The locale for the new sense.
     * @param meaning Target-language words or phrases which are equivalents to the Japanese word.
     * @param partOfSpeech The <cite>Part Of Speech</cite> (POS) for this sense. This set shall
     *        not be modified, since many senses will share the same set.
     */
    public Sense(final Locale locale, final String meaning, final Set<PartOfSpeech> partOfSpeech) {
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
     * @param  locales The locales in <em>reverse</em> of preference order.
     * @param  senses  The other senses to summarize.
     * @return The summarized sense, or {@code null} if not needed.
     */
    static Sense summarize(final Locale[] locales, final Sense[] senses) {
        String            first   = null;
        StringBuilder     buffer  = null;
        Set<PartOfSpeech> largest = Collections.emptySet();
        for (int i=locales.length; --i>=0;) {
            final Locale locale = locales[i];
            for (final Sense candidate : senses) {
                if (locale.equals(candidate.locale)) {
                    if (candidate.partOfSpeech.containsAll(largest)) {
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
            /*
             * The 'first' variable will be non-null if we have found at least one entry for
             * the current locale. In such case, we want to stop the search now. However we
             * will return a non-null value only if we found at least 2 entries.
             */
            if (first != null) {
                if (buffer != null) {
                    return new Summary(locale, buffer.toString(), largest);
                }
                break; // Stop the search since there is one entry in the preferred locale.
            }
        }
        return null;
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
     * Returns a string representation of the grammatical class of the {@link #partOfSpeech} value.
     *
     * @return A string representation of the part of speech, or {@code null} if none.
     */
    public final String getGrammaticalClass() {
        final EnumSet<GrammaticalClass> classes = EnumSet.noneOf(GrammaticalClass.class);
        for (final PartOfSpeech pos : partOfSpeech) {
            classes.add(pos.grammaticalClass);
        }
        String        first  = null;
        StringBuilder buffer = null;
        for (final GrammaticalClass c : classes) {
            if (first == null) {
                first = c.toString();
            } else {
                if (buffer == null) {
                    buffer = new StringBuilder(first);
                }
                buffer.append(", ").append(c);
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
