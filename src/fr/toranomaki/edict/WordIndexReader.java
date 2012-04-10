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
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;


/**
 * Searches words in the file created by {@link fr.toranomaki.edict.writer.WordIndexWriter}.
 *
 * @author Martin Desruisseaux
 */
final class WordIndexReader extends DictionaryFile {
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
     * Number of bytes in the pool of words.
     */
    private final int poolSize;

    /**
     * The character sequences used for the words encoding.
     */
    private final String charSequences;

    /**
     * The position of each character sequences in the {@link #charSequences} string.
     */
    private final int[] encodingMap;

    /**
     * A temporary string builder used for decoding the words.
     */
    private final StringBuilder stringBuilder;

    /**
     * A view over the content of the file created by {@link WordIndexWriter}.
     * The first part of this buffer contains the indexes, and the second part
     * contains the bytes from which to build the words. The separation between
     * those two parts is {@link #numberOfWords} multiplied by the size of the
     * {@code int} type.
     */
    ByteBuffer buffer;

    /**
     * Index of the first valid position for this index in the {@linkplain #buffer}.
     *
     * @see #bufferEndPosition()
     */
    private final int bufferStartPosition;

    /**
     * The index read from the buffer for the first 8 iterations of the {@link #search} method.
     * This is used on the assumption that it may help the OS to reduce the amount of disk seeks.
     * Values in this array are initially zero, and computed when first needed.
     */
    private final int[] packedCache;

    /**
     * A cache of most recently used strings. The cache capacity is arbitrary, but we are
     * better to use a value not greater than a power of 2 time the load factor (0.75).
     */
    @SuppressWarnings("serial")
    private final Map<Integer,String> wordCache = new LinkedHashMap<Integer,String>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * Creates a new index reader.
     *
     * @param  in The file channel from which to read the header.
     * @param  header A buffer containing the header bytes. Must contains 2 integers and one short.
     * @param  isReadingJapanese {@code true} if the dictionary is for Japanese words,
     *         or {@code false} for senses.
     * @throws IOException If an error occurred while reading the file.
     */
    WordIndexReader(final ReadableByteChannel in, final ByteBuffer header,
            final boolean isReadingJapanese, final long bufferStart) throws IOException
    {
        bufferStartPosition = (int) bufferStart;
        if (bufferStartPosition != bufferStart) {
            throw new IOException("Position out of bounds.");
        }
        if (header.getInt() != MAGIC_NUMBER) {
            throw new IOException("Incompatible file format.");
        }
        final int seqPoolSize;
        numberOfWords = header.getInt();
        poolSize      = header.getInt();
        seqPoolSize   = header.getInt();
        encodingMap   = new int[header.getShort() & 0xFFFF];
        final int mapSize = encodingMap.length * (Integer.SIZE / Byte.SIZE);
        ByteBuffer buffer = ByteBuffer.allocate(Math.max(mapSize, seqPoolSize));
        buffer.order(BYTE_ORDER);
        buffer.limit(mapSize);
        readFully(in, buffer);
        buffer.asIntBuffer().get(encodingMap);
        buffer.clear().limit(seqPoolSize);
        readFully(in, buffer);
        charSequences = new String(buffer.array(), 0, seqPoolSize, isReadingJapanese ? JAPAN_ENCODING : LATIN_ENCODING);
        stringBuilder = new StringBuilder();
        packedCache   = new int[256]; // Must be a power of 2.
    }

    /**
     * Returns the end of the mapped portion of the file relevant to this index.
     *
     * @see #bufferStartPosition
     */
    final long bufferEndPosition() {
        return ((long) numberOfWords)*ELEMENT_SIZE + poolSize + bufferStartPosition;
    }

    /**
     * Returns the word at the given packed position.
     */
    private String getWordAt(final int packed) {
        // First, look in the cache.
        final Integer key = packed;
        String word = wordCache.get(key);
        if (word != null) {
            return word;
        }
        // Read from disk, then cache the result.
        final int position = (packed >>> NUM_BITS_FOR_WORD_LENGTH) + numberOfWords*ELEMENT_SIZE;
        int remaining = packed & ((1 << NUM_BITS_FOR_WORD_LENGTH) - 1);
        final ByteBuffer buffer = this.buffer;
        buffer.position(bufferStartPosition + position);
        stringBuilder.setLength(0);
        while (--remaining >= 0) {
            int code = buffer.get() & 0xFF;
            if ((code & MASK_CODE_ON_TWO_BYTES) != 0) {
                assert remaining != 0;
                remaining--;
                code = (code & ~MASK_CODE_ON_TWO_BYTES) | ((buffer.get() & 0xFF) << 7);
            }
            code = encodingMap[code];
            final int start = code >>> Byte.SIZE;
            stringBuilder.append(charSequences, start, start + (code & 0xFF));
        }
        word = stringBuilder.toString();
        wordCache.put(key, word);
        return word;
    }

    /**
     * Searches the entry for the given word. If no exact match is found,
     * returns the first entry right after the given word.
     *
     * @param  word The word to search.
     * @return A partially created entry for the given word.
     */
    final Entry search(final String word) {
        final ByteBuffer buffer = this.buffer;
        final int start = bufferStartPosition;
        int cachePos = 1;
        int low  = 0;
        int high = numberOfWords - 1;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            /*
             * Get a packed reference to the word at index 'mid' (we will extract the actual word
             * later). We will use a cache for the first 8 iterations, because those iterations
             * involve seeks over a large distance. After 8 iterations, the seek distances will
             * be much shorter, so the OS cache will hopefully be used more easily.
             */
            int packed;
            if (cachePos >= 0 && cachePos < packedCache.length) {
                packed = packedCache[cachePos];
                if (packed == 0) {
                    packed = buffer.getInt(mid*ELEMENT_SIZE + start);
                    packedCache[cachePos] = packed;
                }
                cachePos <<= 1;
            } else {
                packed = buffer.getInt(mid*ELEMENT_SIZE + start);
            }
            /*
             * Now get the word at the packed reference and compare it with the word to search.
             * Update the next index (including the index of cached packed references) according
             * the comparison result.
             */
            final String midVal = getWordAt(packed);
            final int c = WordComparator.INSTANCE.compare(midVal, word);
            if (c == 0) {
                return createEntry(mid, midVal);
            }
            if (c < 0) low = mid + 1;
            else     {high = mid - 1; cachePos |= 1;}
        }
        // No need to use the cache, because if we reach this point, we already
        // executed all the iterations so the seek distance is minimal.
        return createEntry(low, getWordAt(buffer.getInt(low*ELEMENT_SIZE + start)));
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
