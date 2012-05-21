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

import fr.toranomaki.edict.Alphabet;
import fr.toranomaki.edict.DictionaryReader;
import fr.toranomaki.grammar.AugmentedEntry;


/**
 * A word to submit to the user for training purpose.
 *
 * @author Martin Desruisseaux
 */
final class LearningWord {
    /**
     * The entry, which will be fetched from the database when first needed.
     */
    private transient AugmentedEntry entry;

    /**
     * The Kanji and reading elements, or {@code null} if none. Those strings are used for locating
     * the entry in the database. They also identified the preferred elements to display.
     */
    private final String kanji, reading;

    /**
     * Creates a new word to learn for the given Kanji and reading elements.
     */
    LearningWord(final  String kanji, final String reading) {
        this.kanji   = kanji;
        this.reading = reading;
    }

    /**
     * Returns the entry associated with this word to learn.
     *
     * @param  dictionary The dictionary to use for fetching the word when needed.
     * @return The entry for the word to learn.
     */
    public AugmentedEntry getEntry(final DictionaryReader dictionary) {
        if (entry == null) {
            final AugmentedEntry[] candidates = dictionary.getEntriesUsingAll(Alphabet.JAPANESE, kanji, reading);
            if (candidates.length != 1) {
                // TODO! Need to report to the user here.
            }
            entry = candidates[0];
        }
        return entry;
    }

    /**
     * Returns a string representation for debugging purpose.
     */
    @Override
    public String toString() {
        return "WordToLearn[" + reading + ']';
    }
}
