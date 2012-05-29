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

import java.util.Arrays;
import java.util.Objects;

import fr.toranomaki.edict.Alphabet;
import fr.toranomaki.edict.DictionaryReader;
import fr.toranomaki.grammar.AugmentedEntry;


/**
 * A word to submit to the user for training purpose.
 *
 * @author Martin Desruisseaux
 */
final class LearningWord extends Data {
    /**
     * The entry, which will be fetched from the database when first needed.
     */
    private transient AugmentedEntry entry;

    /**
     * The Kanji and reading elements, or {@code null} if none. Those strings are used for locating
     * the entry in the database. They also identified the preferred elements to display.
     */
    final String kanji, reading;

    /**
     * Creates a new word to learn for the given Kanji and reading elements.
     */
    LearningWord(final String kanji, final String reading) {
        this.kanji   = trim(kanji);
        this.reading = trim(reading);
    }

    /**
     * Ensures that the given string is either non-empty (ignoring leading and trailing spaces)
     * or null.
     */
    private static String trim(String text) {
        if (text != null) {
            text = text.trim();
            if (text.isEmpty()) {
                text = null;
            }
        }
        return text;
    }

    /**
     * Returns the text to ask to the user for training purpose.
     * If there is no reading element, fallback on the Kanji element.
     */
    public String getQueryText() {
        return (reading != null) ? reading : kanji;
    }

    /**
     * Returns the entry associated with this word to learn.
     *
     * @param  dictionary The dictionary to use for fetching the word when needed.
     * @return The entry for the word to learn, or {@code null} if none.
     */
    public AugmentedEntry getEntry(final DictionaryReader dictionary) {
        if (entry == null) {
            final AugmentedEntry[] candidates = dictionary.getEntriesUsingAll(Alphabet.JAPANESE, kanji, reading);
            switch (candidates.length) {
                case 0: return null;
                case 1: break;
                case 2: Arrays.sort(candidates); break; // Highest priority first.
            }
            entry = candidates[0];
            entry.setLearningWord(kanji, reading);
        }
        return entry;
    }

    /**
     * Compares this word with the given object for equality.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof LearningWord) {
            final LearningWord other = (LearningWord) obj;
            return Objects.equals(kanji,   other.kanji) &&
                   Objects.equals(reading, other.reading);
        }
        return false;
    }

    /**
     * Returns a hash code value for this word.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(kanji) + 31*Objects.hashCode(reading);
    }

    /**
     * Returns a string representation for debugging purpose.
     */
    @Override
    public String toString() {
        return "WordToLearn[" + reading + ']';
    }
}
