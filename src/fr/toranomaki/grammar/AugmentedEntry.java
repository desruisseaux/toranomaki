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
package fr.toranomaki.grammar;

import java.util.Set;
import java.util.EnumSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.Priority;
import fr.toranomaki.edict.PartOfSpeech;


/**
 * Entry augmented with words derived from the Kanji and reading elements.
 * This class apply some grammatical rules in order to obtain derived words.
 *
 * @author Martin Desruisseaux
 */
public final class AugmentedEntry extends Entry {
    /**
     * When an entry contains many word elements, the element to show. This constant is
     * used for making easier to identify the places in the code where only the first
     * element is show and the other elements are ignored.
     */
    public static final int WORD_INDEX = 0;

    /**
     * Mask for the bit to set if the Kanji or reading element of the {@linkplain #entry} is common.
     * This mask is used with the value returned by {@link #getAnnotationMask(boolean)}.
     */
    private static final int COMMON_MASK = 1;

    /**
     * Mask for the bit to set if the Kanji or reading element of the {@linkplain #entry} is the
     * preferred form. This mask is used with the value returned by {@link #getAnnotationMask(boolean)}.
     * At most one of the Kanji and reading elements can have this bit set.
     */
    private static final int PREFERRED_MASK = 2;

    /**
     * The mask to be set if the Kanji elements use at least one non-Joyo Kanji.
     * This mask apply only to the Kanji element.
     */
    private static final int UNCOMMON_KANJI_MASK = 4;

    /**
     * The mask to be set if the entry is used for training.
     * We arbitrarily set this bit on the reading element only.
     */
    private static final int LEARNING_WORD = 4;

    /**
     * Number of bits used by the above masks.
     */
    private static final int NUM_MASK_BITS = 3;

    /**
     * If a word has a priority of at least one of the given types, it will be considered common.
     */
    private static final Set<Priority.Type> COMMONS = EnumSet.of(
            Priority.Type.news, Priority.Type.ichi, Priority.Type.spec);

    /**
     * The derived Kanji or reading words, computed only when first needed.
     *
     * @see #getDerivedWords(boolean)
     */
    private transient String[] derivedKanji, derivedReading;

    /**
     * The senses for each locales in each set of <cite>Part Of Speech</cite>.
     * This map is built when first needed.
     */
    private transient Map<Set<PartOfSpeech>, Map<Locale,String>> senses;

    /**
     * A combination of the above masks, with the highest bits used for the Kanji element
     * and the lowest bits used for the reading element. This field is initially zero and
     * computed when first needed.
     * <p>
     * <b>Implementation note:</b> If the type is expanded to a larger type, remember to
     * replace {@code Byte.MIN_VALUE} by the appropriate min value.
     */
    private transient byte annotations;

    /**
     * Creates an initially empty entry.
     */
    public AugmentedEntry() {
    }

    /**
     * Declares that this entry is used for user training.
     *
     * @param kanji   The preferred Kanji element.
     * @param reading The preferred reading element.
     */
    public synchronized void setLearningWord(final String kanji, final String reading) {
        getAnnotationMask(false); // For annotation computation.
        annotations |= LEARNING_WORD;
        setPreferred(true,  kanji);
        setPreferred(false, reading);
    }

    /**
     * Returns {@code true} if this entry is used for user training.
     * Such entry have the highest priority.
     *
     * @return {@code true} if this entry is used for user training.
     */
    public boolean isLearningWord() {
        return (getAnnotationMask(false) & LEARNING_WORD) != 0;
    }

    /**
     * Returns {@code true} if the Kanji element uses at least one non-Joyo Kanji.
     *
     * @return {@code true} if the Kanji element uses at least one non-Joyo Kanji.
     */
    public boolean isUncommonKanji() {
        return (getAnnotationMask(true) & UNCOMMON_KANJI_MASK) != 0;
    }

    /**
     * Returns {@code true} if the given element is the preferred form.
     *
     * @param  isKanji {@code true} for the Kanji element, or {@code false} for the reading element.
     * @return {@code true} if the given element is the preferred form.
     */
    public boolean isPreferredForm(final boolean isKanji) {
        final int status = getAnnotationMask(isKanji);
        return (status & (COMMON_MASK | PREFERRED_MASK)) == (COMMON_MASK | PREFERRED_MASK);
    }

    /**
     * Returns the derived words, or an empty array if it doesn't apply to this kind of word.
     *
     * @param  isKanji {@code true} for the Kanji element, or {@code false} for the reading element.
     * @return The derived words, or an empty array if none. <strong>Do not modify</strong>
     *         the array content, since this method does not clone the array.
     */
    public final synchronized String[] getDerivedWords(final boolean isKanji) {
        String[] derived = isKanji ? derivedKanji : derivedReading;
        if (derived == null) {
            derived = Grammar.DEFAULT.getDerivedWords(this, isKanji);
            if (isKanji) derivedKanji = derived;
            else       derivedReading = derived;
        }
        return derived;
    }

    /**
     * Returns the senses for each locales in each set of <cite>Part Of Speech</cite>.
     *
     * @return The senses for each locales. This map is not cloned, do not modify.
     */
    public Map<Set<PartOfSpeech>, Map<Locale,String>> getSensesDescriptions() {
        if (senses == null) {
            final Map<Set<PartOfSpeech>, Map<Locale,CharSequence>> senses = new LinkedHashMap<>();
            for (final Sense sense : getSenses()) {
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
            for (final Map<Locale,CharSequence> sense : senses.values()) {
                for (final Map.Entry<Locale,CharSequence> entry : sense.entrySet()) {
                    entry.setValue(entry.getValue().toString());
                }
            }
            // At this point, we replaced every CharSequence by String.
            // So we can cheat with the generic types...
            @SuppressWarnings("unchecked")
            final Map<Set<PartOfSpeech>, Map<Locale,String>> unsafe = (Map) senses;
            this.senses = unsafe;
        }
        return senses;
    }

    /**
     * Returns the status of the Kanji or reading elements as a combination of the
     * {@code *_MASK} constants.
     *
     * @param  isKanji {@code true} for querying the Kanji element, or {@code false} for
     *         querying the reading element.
     * @return A combination of the {@code *_MASK} constants for the requested element.
     */
    private synchronized int getAnnotationMask(final boolean isKanji) {
        int mask = annotations;
        if (mask == 0) {
            int maskKanji   = isCommon(true);
            int maskReading = isCommon(false);
            if (getPriority(false, WORD_INDEX) >= getPriority(true, WORD_INDEX)) {
                maskReading |= PREFERRED_MASK;
            } else {
                maskKanji |= PREFERRED_MASK;
            }
            if (CharacterType.forWord(getWord(true, WORD_INDEX)) == CharacterType.KANJI) {
                maskKanji |= UNCOMMON_KANJI_MASK;
            }
            // Combine in a single value. The MIN_VALUE, which has only the rightmost
            // bit set to 1, is used for ensuring that the value is different than 0.
            mask = (maskKanji << NUM_MASK_BITS) | maskReading | Byte.MIN_VALUE;
            annotations = (byte) mask;
        }
        if (isKanji) {
            mask >>>= NUM_MASK_BITS;
        }
        return mask & ((1 << NUM_MASK_BITS) - 1);
    }

    /**
     * Returns {@link #COMMON_MASK} if the Kanji or reading element is common, or 0 otherwise.
     */
    private int isCommon(final boolean isKanji) {
        final short code = getPriority(isKanji, WORD_INDEX);
        if (code != 0) {
            for (final Priority priority : Priority.fromCode(code)) {
                if (COMMONS.contains(priority.type)) {
                    return COMMON_MASK;
                }
            }
        }
        return 0;
    }

    /**
     * Compares this entry with the given object in order to sort learning words first.
     */
    @Override
    public int compareTo(final Entry other) {
        final boolean ln1 = isLearningWord();
        final boolean ln2 = (other instanceof AugmentedEntry) && ((AugmentedEntry) other).isLearningWord();
        if (ln1 != ln2) {
            return ln1 ? -1 : +1;
        }
        return super.compareTo(other);
    }
}
