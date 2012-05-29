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
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;

import fr.toranomaki.edict.DictionaryReader;
import fr.toranomaki.grammar.AugmentedEntry;
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
     * The {@link LearningWord} instance for a given Kanji or reading element.
     * The values are either {@code LearningWord} or {@code LearningWord[]}.
     * This map is used when a {@linkplain #entryCreated new entry is created},
     * in order to detect if that entry is a learning word.
     */
    private final Map<String,Object> elementsToLearn;

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
        elementsToLearn = new HashMap<>(words.size());
        for (final LearningWord word : initialWordsToLearn) {
            addToMap(word, true);
            addToMap(word, false);
        }
    }

    /**
     * Adds the Kanji or reading elements of the given word to the map of elements.
     * This map is used when a {@linkplain #entryCreated new entry is created}, in
     * order to detect if that entry is a learning word.
     */
    private void addToMap(final LearningWord word, final boolean isKanji) {
        final String element = word.getElement(isKanji);
        if (element != null) {
            final Object old = elementsToLearn.put(element, word);
            if (old != null) {
                LearningWord[] array;
                if (old instanceof LearningWord) {
                    array = new LearningWord[] {(LearningWord) old, word};
                } else {
                    array = (LearningWord[]) old;
                    final int length = array.length;
                    array = Arrays.copyOf(array, length + 1);
                    array[length] = word;
                }
                elementsToLearn.put(element, array);
            }
        }
    }

    /**
     * Adds a new word to the list of words to learn.
     */
    final synchronized void add(final LearningWord word) {
        wordsToLearn.add(word);
        addToMap(word, true);
        addToMap(word, false);
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
    final synchronized void save() throws IOException {
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

    /**
     * Invoked by {@link #getEntryAt(int)} when a new entry has been created.
     * This method verifies if the new word is one of the learning words.
     */
    @Override
    protected void entryCreated(final AugmentedEntry entry) {
        boolean isKanji = true;
        do {
            final int count = entry.getCount(isKanji);
            for (int i=0; i<count; i++) {
                final Object value = elementsToLearn.get(entry.getWord(isKanji, i));
                if (value != null) {
                    /*
                     * Found one or many LearningWord instances which contain a Kanji or
                     * reading elements equals to one of the Kanji or reading elements of
                     * the new entry.
                     */
                    final LearningWord[] array;
                    final int length;
                    if (value instanceof LearningWord[]) {
                        array  = (LearningWord[]) value;
                        length = array.length;
                    } else {
                        array  = null;
                        length = 1;
                    }
                    for (int j=0; j<length; j++) {
                        final LearningWord word = (array != null) ? array[i] : (LearningWord) value;
                        if (word.isForEntry(entry, isKanji)) {
                            entry.setLearningWord(word.kanji, word.reading);
                            return;
                        }
                    }
                }
            }
        } while ((isKanji = !isKanji) == false);
    }
}
