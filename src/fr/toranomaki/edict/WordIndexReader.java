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


/**
 * Searches words in the file created by {@link WordIndexWriter}.
 *
 * @author Martin Desruisseaux
 */
final class WordIndexReader {
    /**
     * Arbitrary magic number. The value on the right side of {@code +} is the version number,
     * to be incremented every time we apply an incompatible change in the file format.
     */
    static final long MAGIC_NUMBER = 8798890475810241902L + 0;

    /**
     * The encoding used for Latin characters.
     */
    static final String LATIN_ENCODING = "UTF-8";

    /**
     * The encoding used for Japanese characters.
     */
    static final String JAPAN_ENCODING = "UTF-16";

    /**
     * Number of bits to use for storing the word length in a {@code int} reference value.
     * The remaining number of bits will be used for storing the word start position.
     */
    static final int NUM_BITS_FOR_LENGTH = 9;

    /**
     * Creates a new index reader.
     */
    WordIndexReader() {
    }

    // TODO
}
