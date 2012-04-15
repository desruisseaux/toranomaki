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
package fr.toranomaki.edict.writer;

import java.util.Set;
import java.util.Arrays;
import fr.toranomaki.edict.WordComparator;


/**
 * The words and their packed location calculated by {@link WordIndexWriter}
 * See {@link WordIndexWriter#writeIndex} for an explanation of the packed
 * {@linkplain #positions} format.
 *
 * @author Martin Desruisseaux
 */
final class WordTable {
    /**
     * The words, sorted in alphabetical order.
     */
    final String[] words;

    /**
     * The packed position of each words in the file.
     */
    final int[] positions;

    /**
     * Prepares a new result for the given collection of words.
     */
    WordTable(final Set<String> words) {
        this.words = words.toArray(new String[words.size()]);
        positions = new int[this.words.length];
        Arrays.sort(this.words, WordComparator.INSTANCE);
    }

    /**
     * Returns the packed position for the given word.
     * The word must exist (this is not explicitely verified by this method).
     */
    int getPackedPosition(final String word) {
        return positions[Arrays.binarySearch(words, word, WordComparator.INSTANCE)];
    }
}
