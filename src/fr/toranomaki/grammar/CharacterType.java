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

import java.util.Arrays;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import fr.toranomaki.Logging;
import fr.toranomaki.edict.Alphabet;


/**
 * The type of character in a word. This enumeration is ordered: if the {@link #JOYO_KANJI}
 * condition is not meet, then the {@link #KANKI} condition is tested. If the {@link #KANJI}
 * condition is not meet neither, then the {@link #KATAKANA} condition is tested an so on.
 *
 * @author Martin Desruisseaux
 */
public enum CharacterType {
    /**
     * The word contains at least one {@linkplain Character#isIdeographic(int) ideographic}
     * character, and all those characters are Jōyō Kanji. The word may also contains other
     * kind of characters in addition of the ideographic ones.
     */
    JOYO_KANJI(Alphabet.JAPANESE, true),

    /**
     * The word contains at least one {@linkplain Character#isIdeographic(int) ideographic}
     * character. It may also contains other kind of characters in addition of the ideographic
     * ones.
     */
    KANJI(Alphabet.JAPANESE, true),

    /**
     * The word does not contain any ideographic character, but contains at least one katakana
     * character. Hiragana or alphabetic characters may also be present.
     */
    KATAKANA(Alphabet.JAPANESE, false),

    /**
     * The word does not contain any ideographic character or katakana, but contains at least
     * one hiragana character. Alphabetic characters may also be present.
     */
    HIRAGANA(Alphabet.JAPANESE, false),

    /**
     * The word does not contain any ideographic character, katakana or hiragana, but contains
     * at least one {@linkplain Character#isAlphabetic(int) alphabetic} character.
     */
    ALPHABETIC(Alphabet.LATIN, false),

    /**
     * If the word does not contains any of the above characters.
     */
    OTHER(null, false);

    /**
     * The alphabet containing this type of character.
     */
    public final Alphabet alphabet;

    /**
     * Convenience flag set to {@code true} for {@link #JOYO_KANJI} and {@link #KANJI},
     * and {@code false} for all other enums.
     */
    public final boolean isKanji;

    /**
     * The Jōyō Kanji, loaded from the {@code "Joyo.txt"} file at class-initialization time.
     * Kanji in this array are sorted.
     */
    private static final char[] JOYO_LIST;
    static {
        char[] kanji;
        final InputStream in = CharacterType.class.getResourceAsStream("Joyo.txt");
        if (in == null) {
            // Resource not found. Should never happen, but let the application run anyway.
            // This method would then always return 'false', so Joyo detection is disabled.
            kanji = new char[0];
        } else {
            int offset = 0;
            kanji = new char[2136]; // Officiel number of Joyo Kanji.
            try (final Reader reader = new InputStreamReader(in, "UTF-8")) {
                int expected;
                while ((expected = kanji.length - offset) > 0) {
                    int upper = reader.read(kanji, offset, expected);
                    if (upper < 0) {
                        throw new EOFException();
                    }
                    // Remove the line feeds.
                    upper += offset;
                    for (int i=offset; i!=upper; i++) {
                        final char r = kanji[i];
                        if ((r > ' ') && (r < 0xFFFE) && (r != 0xFEFF)) {
                            kanji[offset++] = r;
                        }
                    }
                }
            } catch (IOException e) {
                Logging.recoverableException(CharacterType.class, "<cinit>", e);
                // Non fatal error: the application can continue with the Kanji read
                // so far. The test below will adjust the array length accordingly.
            }
            if (offset != kanji.length) {
                // Should never be necessary unless an exception occurred at reading
                // time, but unconditionally check anyway as a paranoiac safety.
                kanji = Arrays.copyOf(kanji, offset);
            }
            Arrays.sort(kanji);
        }
        JOYO_LIST = kanji;
    }

    /**
     * Creates a new enum.
     */
    private CharacterType(final Alphabet alphabet, final boolean isKanji) {
        this.alphabet = alphabet;
        this.isKanji  = isKanji;
    }

    /**
     * Returns the type of characters in the given word.
     *
     * @param  word The word to inspect, or {@code null}.
     * @return The character type, or {@link #OTHER} if none of the known enum,
     *         or {@code null} if the given argument was null or empty.
     */
    public static CharacterType forWord(final String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        boolean isJoyo       = false;
        boolean isKatakana   = false;
        boolean isHiragana   = false;
        boolean isAlphabetic = false;
        for (int i=0; i<word.length();) {
            final int c = word.codePointAt(i);
            if (Character.isIdeographic(c)) {
                isJoyo = Arrays.binarySearch(JOYO_LIST, (char) c) >= 0;
                if (!isJoyo) {
                    // If the character is not a Jōyō Kanji, there is no need to continue
                    // since the contract is to return KANJI as soon as there is at least
                    // one non-Jōyō Kanji.
                    return KANJI;
                }
            } else if (!isJoyo) {
                if (c >= '\u3040' && c < '\u3100') { // Hiragana or Katakana
                    if (c < '\u30A0') { // Hiragana only (not Katakana)
                        isHiragana = true;
                    } else {
                        isKatakana = true;
                    }
                } else if (Character.isAlphabetic(c)) {
                    isAlphabetic = true;
                }
            }
            i += Character.charCount(c);
        }
        if (isJoyo)       return JOYO_KANJI;
        if (isKatakana)   return KATAKANA;
        if (isHiragana)   return HIRAGANA;
        if (isAlphabetic) return ALPHABETIC;
        return OTHER;
    }
}
