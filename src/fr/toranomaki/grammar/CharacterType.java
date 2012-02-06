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


/**
 * The type of character in a word.
 *
 * @author Martin Desruisseaux
 */
public enum CharacterType {
    /**
     * The word contains at least one {@linkplain Character#isIdeographic(int) ideographic}
     * character. It may also contains other kind of characters in addition of the ideographic
     * one.
     */
    KANJI,

    /**
     * The word does not contain any ideographic character, but contains at least one katakana
     * character. Hiragana or alphabetic characters may also be present.
     */
    KATAKANA,

    /**
     * The word does not contain any ideographic character or katakana, but contains at least
     * one hiragana character. Alphabetic characters may also be present.
     */
    HIRAGANA,

    /**
     * The word does not contain any ideographic character, katakana or hiragana, but contains
     * at least one {@linkplain Character#isAlphabetic(int) alphabetic} character.
     */
    ALPHABETIC,

    /**
     * If the word does not contains any of the above characters.
     */
    OTHER;

    /**
     * Returns the type of characters in the given word.
     *
     * @param  word The word to inspect.
     * @return The character type, or {@link #OTHER} if none of the known enum.
     */
    public static CharacterType forWord(final String word) {
        boolean isKatakana   = false;
        boolean isHiragana   = false;
        boolean isAlphabetic = false;
        for (int i=0; i<word.length();) {
            final int c = word.codePointAt(i);
            if (Character.isIdeographic(c)) {
                return KANJI;
            }
            if (c >= '\u3040' && c < '\u3100') { // Hiragana or Katakana
                if (c < '\u30A0') { // Hiragana only (not Katakana)
                    isHiragana = true;
                } else {
                    isKatakana = true;
                }
            } else if (Character.isAlphabetic(c)) {
                isAlphabetic = true;
            }
            i += Character.charCount(c);
        }
        if (isKatakana)   return KATAKANA;
        if (isHiragana)   return HIRAGANA;
        if (isAlphabetic) return ALPHABETIC;
        return OTHER;
    }
}
