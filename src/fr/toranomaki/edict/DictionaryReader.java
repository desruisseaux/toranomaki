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

import java.util.List;
import java.util.ArrayList;
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
public final class DictionaryReader extends DictionaryFile {
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
     * @param  file The dictionary file to open.
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
        final List<String> addTo = new ArrayList<>(1);
        wordIndex[getLanguageIndex(isJapanese)].search(word, addTo);
        return addTo.get(0);
    }
}
