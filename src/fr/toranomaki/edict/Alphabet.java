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
 * The writing system of a word stored in the dictionary.
 *
 * @author Martin Desruisseaux
 */
public enum Alphabet {
    /**
     * A Kanji or reading elements.
     */
    JAPANESE("UTF-16"),

    /**
     * English, French or German word.
     */
    LATIN("UTF-8"),

    /**
     * Cyrillic alphabet.
     */
    CYRILLIC("UTF-16");

    /**
     * The name of the character encoding used for this alphabet. Those encodings are used only
     * in the header of the binary file. After the header, we will use our own encoding which
     * will replace the most frequent character sequence by a code.
     */
    public final String encoding;

    /**
     * Creates a new enum.
     */
    private Alphabet(final String encoding) {
        this.encoding = encoding;
    }
}
