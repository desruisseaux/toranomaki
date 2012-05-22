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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;

import fr.toranomaki.edict.Alphabet;
import fr.toranomaki.edict.DictionaryReader;
import fr.toranomaki.grammar.AugmentedEntry;
import fr.toranomaki.grammar.CharacterType;


/**
 * A word to submit to the user for training purpose.
 *
 * @author Martin Desruisseaux
 */
final class LearningWord extends Data {
    /**
     * The separator character to use when saving the list of words.
     * This is the separator to put between Kanji and reading elements.
     */
    private static final char SEPARATOR = '\t';

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
     * Returns the file in which to save the words list.
     */
    private static File getFile() throws IOException {
        return getDirectory().resolve("LearningWords.txt").toFile();
    }

    /**
     * Loads the list of words from the last saved session. If no session was found,
     * returns a single arbitrary word. This is needed because {@link LearningPane}
     * is not designed for working with an empty list.
     *
     * @return The words, never empty.
     * @throws IOException If an error occurred while loading the words.
     */
    static LearningWord[] load() throws IOException {
        final Set<LearningWord> words = new LinkedHashSet<>(64);
        final File file = getFile();
        if (file.isFile()) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), FILE_ENCODING))) {
                String line; while ((line = in.readLine()) != null) {
                    if (!(line = line.trim()).isEmpty()) {
                        if (line.charAt(0) == BYTE_ORDER_MARK) {
                            line = line.substring(1).trim();
                            if (line.isEmpty()) continue;
                        }
                        int s = line.indexOf(SEPARATOR);
                        if (s <= 0) { // Should not happen, unless the user edited the file himselve.
                            s = line.indexOf(' ');
                            if (s <= 0) {
                                // Should not happen neither, unless the user edited the file.
                                // We have a single word; try to guess if it is Kanji or reading.
                                s = CharacterType.forWord(line).isKanji ? line.length()-1 : 0;
                            }
                        }
                        words.add(new LearningWord(line.substring(0,s), line.substring(s+1)));
                    }
                }
            }
        }
        if (words.isEmpty()) {
            words.add(new LearningWord("お早う", "おはよう"));
        }
        return words.toArray(new LearningWord[words.size()]);
    }

    /**
     * Saves the content of the given words array.
     *
     * @throws IOException If an error occurred while saving.
     */
    static void save(final LearningWord[] words) throws IOException {
        final File file = getFile();
        if (words.length == 0) {
            file.delete();
        } else {
            final String lineSeparator = System.lineSeparator();
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file), FILE_ENCODING)) {
                out.write(BYTE_ORDER_MARK);
                for (final LearningWord word : words) {
                    if (word.kanji != null) {
                        out.write(word.kanji);
                    }
                    out.write(SEPARATOR);
                    if (word.reading != null) {
                        out.write(word.reading);
                    }
                    out.write(lineSeparator);
                }
            }
        }
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
