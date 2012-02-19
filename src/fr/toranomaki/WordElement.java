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
package fr.toranomaki;

import java.util.Set;
import java.util.EnumSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.sql.SQLException;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.JMdict;
import fr.toranomaki.edict.Priority;
import fr.toranomaki.edict.PartOfSpeech;
import fr.toranomaki.grammar.CharacterType;


/**
 * A row to be shown in the {@link WordTable}. This row combine the {@link Entry} to be
 * shown, together with some rendering information.
 *
 * @author Martin Desruisseaux
 */
final class WordElement {
    /**
     * When an entry contains many word elements, the element to show. This constant is
     * used for making easier to identify the places in the code where only the first
     * element is show and the other elements are ignored.
     */
    static final int WORD_INDEX = 0;

    /**
     * Mask for the bit to set if the Kanji or reading element of the {@linkplain #entry} is common.
     * This mask is used with the value returned by {@link #getAnnotationMask(boolean)}.
     */
    static final int COMMON_MASK = 1;

    /**
     * Mask for the bit to set if the Kanji or reading element of the {@linkplain #entry} is the
     * preferred form. This mask is used with the value returned by {@link #getAnnotationMask(boolean)}.
     */
    static final int PREFERRED_MASK = 2;

    /**
     * Number of bits used by the above masks.
     */
    private static final int NUM_MASK_BITS = 2;

    /**
     * If a word has a priority of at least one of the given types, it will be considered common.
     */
    private static final Set<Priority.Type> COMMONS = EnumSet.of(
            Priority.Type.news, Priority.Type.ichi, Priority.Type.spec);

    /**
     * The EDICT entry to be show.
     */
    final Entry entry;

    /**
     * A combination of the above masks, with the highest bits used for the Kanji element
     * and the lowest bits used for the reading element.
     */
    private final int annotations;

    /**
     * The senses for each locales and collection of <cite>Part Of Speech</cite>.
     * This map is built when first needed.
     */
    private Map<Set<PartOfSpeech>, Map<Locale, CharSequence>> senses;

    /**
     * Creates a new row for the given entry.
     *
     * @param  dictionary   The dictionary used for building the entry.
     * @param  entry        The entry.
     * @throws SQLException If an error occurred while fetching additional information
     *                      from the database.
     */
    WordElement(final JMdict dictionary, final Entry entry) throws SQLException {
        this.entry      = entry;
        int maskKanji   = isCommon(dictionary, entry, true);
        int maskReading = isCommon(dictionary, entry, false);
        if (CharacterType.forWord(entry.getWord(true, WORD_INDEX)) == CharacterType.JOYO_KANJI) {
            maskKanji |= PREFERRED_MASK;
        }
        if (entry.getPriority(false, WORD_INDEX) > entry.getPriority(true, WORD_INDEX)) {
            maskReading |= PREFERRED_MASK;
        }
        annotations = (maskKanji << NUM_MASK_BITS) | maskReading;
    }

    /**
     * Returns {@link #COMMON_MASK} if the Kanji or reading element is common, or 0 otherwise.
     */
    private static int isCommon(final JMdict dictionary, final Entry entry, final boolean isKanji) throws SQLException {
        final short code = entry.getPriority(isKanji, WORD_INDEX);
        if (code != 0) {
            for (final Priority priority : dictionary.getPriority(code)) {
                if (COMMONS.contains(priority.type)) {
                    return COMMON_MASK;
                }
            }
        }
        return 0;
    }

    /**
     * Returns the status of the Kanji or reading elements as a combination of the
     * {@code *_MASK} constants.
     *
     * @param  isKanji {@code true} for querying the Kanji element, or {@code false} for
     *         querying the reading element.
     * @return A combination of the {@code *_MASK} constants for the requested element.
     */
    final int getAnnotationMask(final boolean isKanji) {
        int mask = annotations;
        if (isKanji) {
            mask >>>= NUM_MASK_BITS;
        }
        return mask & ((1 << NUM_MASK_BITS) - 1);
    }

    /**
     * Returns the senses for each locales and collection of <cite>Part Of Speech</cite>.
     */
    final Map<Set<PartOfSpeech>, Map<Locale, CharSequence>> getSenses() {
        if (senses == null) {
            senses = new LinkedHashMap<>();
            for (final Sense sense : entry.getSenses()) {
                Map<Locale, CharSequence> localized = senses.get(sense.partOfSpeech);
                if (localized == null) {
                    localized = new LinkedHashMap<>();
                    senses.put(sense.partOfSpeech, localized);
                }
                CharSequence meaning = localized.get(sense.locale);
                if (meaning == null) {
                    meaning = sense.meaning;
                    localized.put(sense.locale, meaning);
                } else {
                    final StringBuilder buffer;
                    if (meaning instanceof StringBuilder) {
                        buffer = (StringBuilder) meaning;
                    } else {
                        buffer = new StringBuilder(meaning);
                        localized.put(sense.locale, buffer);
                    }
                    buffer.append(", ").append(sense.meaning);
                }
            }
            /*
             * Replaces all StringBuilder by String instances.
             */
            for (final Map<Locale, CharSequence> sense : senses.values()) {
                for (final Map.Entry<Locale, CharSequence> entry : sense.entrySet()) {
                    entry.setValue(entry.getValue().toString());
                }
            }
        }
        return senses;
    }
}
