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

import java.util.Locale;
import java.util.Collections;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


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
    private final ByteBuffer buffer;

    /**
     * Index in the {@linkplain #buffer} where the list of entries begin.
     */
    private final int entryListsPoolStart;

    /**
     * Index in the {@linkplain #buffer} where the definition of entries begin.
     */
    private final int entryDefinitionsStart;

    /**
     * Creates a new reader for the given binary file.
     *
     * @param  file The dictionary file to open, typically {@link #getDictionaryFile()}.
     * @throws IOException If an error occurred while reading the file.
     */
    public DictionaryReader(final Path file) throws IOException {
        wordIndex = new WordIndexReader[2];
        final ByteBuffer header = ByteBuffer.allocate(
                4 * (Integer.SIZE / Byte.SIZE) +
                1 * (Short  .SIZE / Byte.SIZE));
        header.order(BYTE_ORDER);
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            /*
             * Initialize the index of words. Note that the WordIndexReader
             * constructor will read more data beyond the 'header' buffer.
             */
            long position = 0;
            for (int i=0; i<wordIndex.length; i++) {
                header.clear();
                readFully(in, header);
                wordIndex[i] = new WordIndexReader(in, header, getLanguageAt(i), position);
                position = wordIndex[i].bufferEndPosition();
            }
            /*
             * Read remaining header data and map the buffer.
             */
            header.clear().limit(2 * Integer.SIZE / Byte.SIZE);
            readFully(in, header);
            entryListsPoolStart   = (int) position; position += header.getInt();
            entryDefinitionsStart = (int) position; position += header.getInt();
            buffer = in.map(FileChannel.MapMode.READ_ONLY, in.position(), position);
            buffer.order(BYTE_ORDER);
        }
        for (int i=0; i<wordIndex.length; i++) {
            wordIndex[i].buffer = buffer;
        }
    }

    /**
     * Searches the given word. If no exact match is found, returns the first word
     * after the given word.
     *
     * @param  word The word to search.
     * @param  isJapanese {@code true} for searching a Japanese word, or {@code false} for a sense.
     * @return A word equals or sorted after the given word.
     */
    public String search(final String word, final boolean isJapanese) {
        final WordIndexReader index = wordIndex[getLanguageIndex(isJapanese)];
        return index.getWordAt(index.search(word));
    }

    /**
     * Returns all entries associated to the word at the given index.
     *
     * @param  wordIndex Index of the word to search.
     * @param  isJapanese {@code true} for a Japanese word, or {@code false} for a sense.
     * @return All entries associated to the word at the given index.
     */
    public Entry[] getEntries(final int wordIndex, final boolean isJapanese) {
        final WordIndexReader index = this.wordIndex[getLanguageIndex(isJapanese)];
        int position = index.getEntryListPackedPosition(wordIndex);
        final int length = position & 0xFF;
        position >>>= Byte.SIZE;
        buffer.position(entryListsPoolStart + position*NUM_BYTES_FOR_ENTRY_POSITION);
        final int[] references = new int[length];
        for (int i=0; i<length; i++) {
            int ref = buffer.get() & 0xFF;
            if (NUM_BYTES_FOR_ENTRY_POSITION >= 2) {
                ref |= (buffer.get() & 0xFF) << Byte.SIZE;
                if (NUM_BYTES_FOR_ENTRY_POSITION >= 3) {
                    ref |= (buffer.get() & 0xFF) << (2*Byte.SIZE);
                    if (NUM_BYTES_FOR_ENTRY_POSITION >= 4) {
                        throw new AssertionError(NUM_BYTES_FOR_ENTRY_POSITION);
                    }
                }
            }
            references[i] = ref;
        }
        final Entry[] entries = new Entry[length];
        for (int i=0; i<length; i++) {
            entries[i] = getEntryAt(references[i]);
        }
        return entries;
    }

    /**
     * Gets the entry at the given position.
     *
     * @param  position Entry position, in bytes relative to the beginning of the entry pool.
     * @return The entry at the given index.
     */
    private Entry getEntryAt(final int position) {
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
        final int[]   words      = new int  [numJapaneses + numSenses];
        final short[] priorities = new short[numJapaneses];
        for (int i=0; i<words.length; i++) {
            words[i] = buffer.getInt();
            if (i < numJapaneses) {
                priorities[i] = buffer.getShort();
            }
        }
        /*
         * Now build the entry. Note that the call to WordIndexReader.getWordAtPacked(int)
         * will change the buffer position, which is why we needed to extract all pointers
         * first.
         */
        final Entry entry = new Entry(position);
        WordIndexReader index = wordIndex[getLanguageIndex(true)];
        for (int i=0; i<numJapaneses; i++) {
            entry.add(i < numKanjis, index.getWordAtPacked(words[i]), priorities[i]);
        }
        index = wordIndex[getLanguageIndex(false)];
        for (int i=numJapaneses; i<words.length; i++) {
            entry.addSense(new Sense(Locale.ENGLISH, index.getWordAtPacked(words[i]), Collections.<PartOfSpeech>emptySet()));
        }
        return entry;
    }
}
