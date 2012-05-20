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

import fr.toranomaki.edict.Entry;


/**
 * Entry augmented with words derived from the Kanji and reading elements.
 * This class apply some grammatical rules in order to obtain derived words.
 *
 * @author Martin Desruisseaux
 */
public final class AugmentedEntry extends Entry {
    /**
     * The derived Kanji or reading words, computed only when first needed.
     *
     * @see #getDerivedWords(boolean)
     */
    private transient String[] derivedKanji, derivedReading;

    /**
     * Creates an initially empty entry.
     */
    public AugmentedEntry() {
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
}
