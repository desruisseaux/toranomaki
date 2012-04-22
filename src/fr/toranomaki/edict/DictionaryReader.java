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

import java.util.Set;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import fr.toranomaki.grammar.CharacterType;


/**
 * The reader of the dictionary binary file.
 *
 * @author Martin Desruisseaux
 */
public final class DictionaryReader extends BinaryData {
    /**
     * The index. For now we support only Japanese language and senses in westerner languages.
     * But we define this field as an array anyway in order to make easier the addition of new
     * languages in a future version, if desired.
     */
    private final WordIndexReader[] wordIndex;

    /**
     * A view over a portion of the file created by the {@link fr.toranomaki.edict.writer}
     * package. This is a view of all the remaining part of the binary file after the header.
     */
    final ByteBuffer buffer;

    /**
     * Index in the {@linkplain #buffer} where the list of entries begin.
     */
    final int entryListsPoolStart;

    /**
     * Index in the {@linkplain #buffer} where the definition of entries begin.
     */
    private final int entryDefinitionsStart;

    /**
     * The part of speech sets used in the binary file.
     */
    private final PartOfSpeechSet[] partOfSpeechSets;

    /**
     * Creates a new reader for the default binary file.
     *
     * @throws IOException If an error occurred while reading the file.
     */
    public DictionaryReader() throws IOException {
        this(getDictionaryFile());
    }

    /**
     * Creates a new reader for the given binary file.
     *
     * @param  file The dictionary file to open, typically {@link #getDictionaryFile()}.
     * @throws IOException If an error occurred while reading the file.
     */
    public DictionaryReader(final Path file) throws IOException {
        final Alphabet[] alphabets = Alphabet.values();
        wordIndex = new WordIndexReader[2];
        final ByteBuffer header = ByteBuffer.allocate(4096);
        header.order(BYTE_ORDER);
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            /*
             * Initialize the index of words. Note that the WordIndexReader
             * constructor will read more data beyond the 'header' buffer.
             */
            long position = 0;
            for (int i=0; i<wordIndex.length; i++) {
                header.clear().limit(4 * (Integer.SIZE / Byte.SIZE) +
                                     1 * (Short  .SIZE / Byte.SIZE));
                readFully(in, header);
                wordIndex[i] = new WordIndexReader(this, in, header, alphabets[i], position);
                position = wordIndex[i].bufferEndPosition();
            }
            /*
             * Read remaining header data, then constructs the sets of Part Of Speech (POS).
             * We should have a raisonably small amount of set of POS (about 400).
             */
            header.clear().limit(3 * (Integer.SIZE / Byte.SIZE));
            readFully(in, header);
            entryListsPoolStart   = (int) position; position += header.getInt();
            entryDefinitionsStart = (int) position; position += header.getInt();
            partOfSpeechSets      = new PartOfSpeechSet[header.getInt()];
            header.clear().limit(partOfSpeechSets.length * (Long.SIZE / Byte.SIZE));
            readFully(in, header);
            for (int i=0; i<partOfSpeechSets.length; i++) {
                partOfSpeechSets[i] = new PartOfSpeechSet(header.getLong());
            }
            /*
             * Map the buffer.
             */
            buffer = in.map(FileChannel.MapMode.READ_ONLY, in.position(), position);
            buffer.order(BYTE_ORDER);
        }
    }

    /**
     * Searches the index of the given word. If no exact match is found, returns the
     * "insertion point" with all bits reversed (same convention than
     * {@link java.util.Arrays#binarySearch(Object[], Object)}).
     *
     * @param  word The word to search.
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @return The index of the given word, or the insertion point with all bits reversed.
     */
    public int getWordIndex(final String word, final Alphabet alphabet) {
        return wordIndex[alphabet.ordinal()].getWordIndex(word);
    }

    /**
     * Returns the word at the given index. The index is typically a value returned by
     * {@link #getWordIndex(String, boolean)}.
     *
     * @param  wordIndex Index of the word to search.
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @return The word at the given index.
     * @throws IndexOutOfBoundsException If the given index is out of bounds.
     */
    public String getWordAt(final int wordIndex, final Alphabet alphabet) throws IndexOutOfBoundsException {
        return this.wordIndex[alphabet.ordinal()].getWordAt(wordIndex);
    }

    /**
     * Returns all entries associated to given word, or an empty array of none.
     *
     * @param  word The word to search.
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @return All entries associated to the given word.
     */
    public Entry[] getEntriesUsingWord(final String word, final Alphabet alphabet) {
        return this.wordIndex[alphabet.ordinal()].getEntriesUsingWord(word);
    }

    /**
     * Returns all entries associated to the word at the given index.
     *
     * @param  wordIndex Index of the word to search.
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @return All entries associated to the word at the given index.
     */
    public Entry[] getEntriesUsingWord(final int wordIndex, final Alphabet alphabet) {
        return this.wordIndex[alphabet.ordinal()].getEntriesUsingWord(wordIndex);
    }

    /**
     * Gets the entry at the given position.
     * This is a callback method for {@link WordIndexReader} only.
     *
     * @param  position Entry position, in bytes relative to the beginning of the entry pool.
     * @return The entry at the given index.
     */
    final Entry getEntryAt(final int position) {
        buffer.position(entryDefinitionsStart + position);
        /*
         * Get the number of Japanese words and the number of senses.
         */
        int numKanjis = buffer.get() & 0xFF;
        final int numReadings = numKanjis & ((1 << NUM_BITS_FOR_ELEMENT_COUNT) - 1);
        numKanjis >>>= NUM_BITS_FOR_ELEMENT_COUNT;
        final int numJapaneses = numKanjis + numReadings;
        final int numSenses = buffer.get() & 0xFF;
        /*
         * Extract all entry data now. This include pointer to words,
         * but we will not resolve those pointers yet.
         */
        final int[]   wordRefs   = new int  [numJapaneses + numSenses];
        final short[] attributes = new short[numJapaneses + numSenses];
        for (int i=0; i<wordRefs.length; i++) {
            wordRefs  [i] = buffer.getInt();
            attributes[i] = buffer.getShort();
        }
        /*
         * Now build the entry. Note that the call to WordIndexReader.getWordAtPacked(int)
         * will change the buffer position, which is why we needed to extract all pointers
         * first.
         */
        final Entry entry = new Entry(position);
        WordIndexReader index = wordIndex[Alphabet.JAPANESE.ordinal()];
        for (int i=0; i<numJapaneses; i++) {
            entry.add(i < numKanjis, index.getWordAtPacked(wordRefs[i]), attributes[i]);
        }
        index = wordIndex[Alphabet.LATIN.ordinal()];
        for (int i=numJapaneses; i<wordRefs.length; i++) {
            final String word = index.getWordAtPacked(wordRefs[i]);
            final short  attr = attributes[i];
            final int    lang = (attr & ((1 << NUM_BITS_FOR_LANGUAGE) - 1));
            entry.addSense(new Sense(LANGUAGES[lang], word, partOfSpeechSets[attr >>> NUM_BITS_FOR_LANGUAGE]));
        }
        return entry;
    }

    /**
     * Returns the set of priority from the given code.
     *
     * @param  code The code from which to get the set of priorities.
     * @return The set of priorities from the given code.
     *
     * @todo Not yet implemented.
     */
    public Set<Priority> getPriority(final short code) {
        return java.util.Collections.emptySet();
    }

    /**
     * Searches the best entry matching the given text, or {@code null} if none.
     *
     * @param toSearch       The word to search.
     * @param documentOffset Index of the first character of the given word in the document.
     *        This information is not used by this method. This value is simply stored in the
     *        {@link SearchResult#documentOffset} field for caller convenience.
     * @return The search result, or {@code null} if none.
     *
     * @todo Not yet implemented.
     */
    public SearchResult searchBest(final String toSearch, final int documentOffset) {
        if (toSearch == null || toSearch.isEmpty()) {
            return null;
        }
        String racine = toSearch;
        final CharacterType type = CharacterType.forWord(toSearch);
        if (type.isKanji) {
            final int length = toSearch.length();
            for (int i=0; i<length;) {
                final int c = toSearch.codePointAt(i);
                if (Character.isIdeographic(c)) {
                    i += Character.charCount(c);
                    continue;
                }
                // Found the first non-ideographic character. If we have at least one
                // ideographic character, we will use is as the root of the words to search.
                if (i != 0) {
                    racine = toSearch.substring(0, i);
                }
                break;
            }
        }
        return null;
        //return SearchResult.search(search(racine, type), toSearch, type.isKanji, documentOffset);
    }
}
