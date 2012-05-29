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

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;

import fr.toranomaki.edict.DictionaryReader;
import fr.toranomaki.grammar.CharacterType;


/**
 * The dictionary used by the application. In addition to the dictionary, this class manages
 * also a list of words used for user training. Only one instance of this class should exist
 * in the application.
 *
 * @author Martin Desruisseaux
 */
final class Dictionary extends DictionaryReader {
    /**
     * The separator character to use when saving the list of learning words.
     * This is the separator to put between Kanji and reading elements.
     */
    private static final char SEPARATOR = '\t';

    /**
     * The list of words to learn. This list is sorted from easiest to more difficult words,
     * words, as indicated by the user by clicking on the "Easy" or "Hard" buttons.
     */
    final List<LearningWord> wordsToLearn;

    /**
     * A snapshot of the {@link #wordsToLearn} list at construction time.
     * This is used before to {@linkplain #save() save} the list of words
     * in order to detect if this list has changed.
     */
    private final LearningWord[] initialWordsToLearn;

    /**
     * Creates a new dictionary instance. After the dictionary has been initialized, this
     * constructors loads  the list of learning words from the last saved session. If no
     * session was found, creates a single arbitrary word. This is needed because
     * {@link LearningPane} is not designed for working with an empty list.
     *
     * @throws IOException If an error occurred while reading the file.
     */
    public Dictionary() throws IOException {
        super();
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
        wordsToLearn = new ArrayList<>(words);
        initialWordsToLearn = wordsToLearn.toArray(new LearningWord[wordsToLearn.size()]);
    }

    /**
     * Returns the file in which to save the words list.
     */
    private static File getFile() throws IOException {
        return getDirectory().resolve("LearningWords.txt").toFile();
    }

    /**
     * Saves the list of learning words.
     *
     * @throws IOException If an error occurred while saving.
     */
    final void save() throws IOException {
        if (wordsToLearn.equals(Arrays.asList(initialWordsToLearn))) {
            return;  // No change in the list of words, so do nothing.
        }
        final String lineSeparator = System.lineSeparator();
        try (Writer out = new OutputStreamWriter(new FileOutputStream(getFile()), FILE_ENCODING)) {
            out.write(BYTE_ORDER_MARK);
            for (final LearningWord word : wordsToLearn) {
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
