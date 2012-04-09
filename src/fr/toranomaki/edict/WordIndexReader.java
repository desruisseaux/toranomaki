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

import java.util.Map;
import java.util.LinkedHashMap;
import java.io.IOException;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


/**
 * Searches words in the file created by {@link fr.toranomaki.edict.writer.WordIndexWriter}.
 *
 * @author Martin Desruisseaux
 */
public final class WordIndexReader extends DictionaryFile {
    /**
     * The cache capacity. This value is arbitrary, but we are better to use a value
     * not greater than a power of 2 time the load factor (0.75).
     */
    private static final int CACHE_SIZE = 3000;

    /**
     * Number of words in the mapped index.
     */
    private final int numberOfWords;

    /**
     * A view over the content of the file created by {@link WordIndexWriter}.
     * The first part of this buffer contains the indexes, and the second part
     * contains the bytes from which to build the words. The separation between
     * those two parts is {@link #numberOfWords} multiplied by the size of the
     * {@code int} type.
     */
    private final ByteBuffer buffer;

    /**
     * The decoder to use for converting the bytes from the {@link #buffer} to
     * {@link String} instances.
     */
    private final CharsetDecoder decoder;

    /**
     * The buffer where to put decoder words.
     */
    private final CharBuffer charBuffer;

    /**
     * A cache of most recently used strings. The cache capacity is arbitrary, but we are
     * better to use a value not greater than a power of 2 time the load factor (0.75).
     */
    @SuppressWarnings("serial")
    private final Map<Integer,String> cache = new LinkedHashMap<Integer,String>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * Creates a new index reader.
     *
     * @param  file The file to open.
     * @param  isReadingJapanese {@code true} if the dictionary is for Japanese words,
     *         or {@code false} for senses.
     * @throws IOException If an error occurred while reading the file.
     */
    public WordIndexReader(final Path file, final boolean isReadingJapanese) throws IOException {
        decoder = Charset.forName(isReadingJapanese ? JAPAN_ENCODING : LATIN_ENCODING).newDecoder();
        charBuffer = CharBuffer.allocate(1 << NUM_BITS_FOR_WORD_LENGTH);
        final ByteBuffer header = ByteBuffer.allocate(4 * ELEMENT_SIZE);
        header.order(BYTE_ORDER);
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            readFully(in, header);
            header.flip();
            if (header.getLong() != MAGIC_NUMBER) {
                throw new IOException(file + ": incompatible file format.");
            }
            numberOfWords = header.getInt();
            final int poolLength = header.getInt();
            assert !header.hasRemaining() : header;
            buffer = in.map(FileChannel.MapMode.READ_ONLY, header.position(),
                    ((long) numberOfWords) * ELEMENT_SIZE + poolLength);
            buffer.order(BYTE_ORDER);
        }
    }

    /**
     * Reads bytes from the given channel until the given buffer is full.
     */
    private static void readFully(final ReadableByteChannel in, final ByteBuffer buffer) throws IOException {
        do if (in.read(buffer) < 0) {
            throw new EOFException();
        } while (buffer.hasRemaining());
    }

    /**
     * Returns the word at the given packed position.
     */
    private String getWordAt(final int packed) {
        // First, look in the cache.
        final Integer key = packed;
        String word = cache.get(key);
        if (word != null) {
            return word;
        }
        // Read from disk, then cache the result.
        final int position = (packed >>> NUM_BITS_FOR_WORD_LENGTH) + numberOfWords*ELEMENT_SIZE;
        final int length = packed & ((1 << NUM_BITS_FOR_WORD_LENGTH) - 1);
        buffer.limit(position + length).position(position);
        charBuffer.clear();
        decoder.reset();
        CoderResult result = decoder.decode(buffer, charBuffer, true);
        if (result == CoderResult.UNDERFLOW) {
            result = decoder.flush(charBuffer);
            if (result == CoderResult.UNDERFLOW) {
                charBuffer.flip();
                word = charBuffer.toString();
                cache.put(key, word);
                return word;
            }
        }
        throw new RuntimeException("Malformed input encoding: " + result);
    }

    /**
     * Searches the entry for the given word. If no exact match is found,
     * returns the first entry right after the given word.
     *
     * @param  word The word to search.
     * @return A partially created entry for the given word.
     */
    public Entry search(final String word) {
        int low  = 0;
        int high = numberOfWords - 1;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            final String midVal = getWordAt(buffer.getInt(mid * ELEMENT_SIZE));
            final int c = WordComparator.INSTANCE.compare(midVal, word);
            if (c == 0) {
                return createEntry(mid, midVal);
            }
            if (c < 0) low = mid + 1;
            else      high = mid - 1;
        }
        return createEntry(low, getWordAt(buffer.getInt(low * ELEMENT_SIZE)));
    }

    /**
     * Creates a new entry for the given word, which has been found at the given index.
     *
     * @param index The index where the word has been found.
     * @param word  The word which has been found.
     */
    private static Entry createEntry(final int index, final String word) {
        final Entry entry = new Entry(index);
        entry.add(false, word, (short) 0);
        return entry;
    }
}
