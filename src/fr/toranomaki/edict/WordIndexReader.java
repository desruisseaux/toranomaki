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

import java.util.Arrays;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;


/**
 * Searches words in the file created by {@link fr.toranomaki.edict.writer.WordIndexWriter}.
 * <p>
 * This class is not thread-safe. It is user responsibility to ensure that instances of this
 * class are used in only one thread.
 *
 * @author Martin Desruisseaux
 */
final class WordIndexReader extends BinaryData {
    /**
     * The array to returns from the search method when no matching entry has been found.
     */
    static final Entry[] EMPTY_RESULT = new Entry[0];

    /**
     * The dictionary which contain this index. The {@link DictionaryReader#buffer} shall be
     * a view over the content of the file created by {@link WordIndexWriter}. The first part
     * of that buffer contains the indexes, and the second part contains the bytes from which
     * to build the words. The separation between those two parts is {@link #numberOfWords}
     * multiplied by the size of the {@code int} type.
     */
    private final DictionaryReader dictionary;

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
     * The position (24 bits) and length (8 bits) of each character sequences
     * in the {@link #charSequences} string.
     */
    private final int[] encodingMap;

    /**
     * A temporary string builder used for decoding the words.
     */
    private final StringBuilder stringBuilder;

    /**
     * Index of the first valid position for this index in the {@linkplain #buffer}.
     *
     * @see #bufferEndPosition()
     */
    private final int bufferStartPosition;

    /**
     * Position where the references to entry lists begin.
     */
    private final int entryListRefStartPosition;

    /**
     * The words read from the buffer for the first 8 iterations of the {@link #getWordIndex} method.
     * This is used on the assumption that it may help the OS to reduce the amount of disk seeks.
     * Values in this array are initially null, and computed when first needed.
     */
    private final String[] wordByIteration;

    /**
     * A cache of most recently used strings.
     */
    private final WeakStringMap wordbyPackedIndex = new WeakStringMap();

    /**
     * Creates a new index reader.
     *
     * @param  dictionary The dictionary which contain this index.
     * @param  in The file channel from which to read the header.
     * @param  header A buffer containing the header bytes. Must contains 2 integers and one short.
     * @param  alphabet Identifies the index encoding.
     * @throws IOException If an error occurred while reading the file.
     */
    WordIndexReader(final DictionaryReader dictionary, final ReadableByteChannel in, final ByteBuffer header,
            final Alphabet alphabet, final long bufferStart) throws IOException
    {
        this.dictionary = dictionary;
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
        charSequences   = new String(buffer.array(), 0, seqPoolSize, alphabet.encoding);
        stringBuilder   = new StringBuilder();
        wordByIteration = new String[256]; // Must be a power of 2.
        entryListRefStartPosition = bufferStartPosition + numberOfWords*NUM_BYTES_FOR_INDEX_ELEMENT + poolSize;
    }

    /**
     * Returns the end of the mapped portion of the file relevant to this index.
     *
     * @see #bufferStartPosition
     */
    final long bufferEndPosition() {
        /*
         * NUM_BYTES_FOR_INDEX_ELEMENT is used once for the index created by WordIndexWriter,
         * and once again for the "word to entries" map created by 'WordToEntries' class.
         */
        long position = ((long) numberOfWords) * (NUM_BYTES_FOR_INDEX_ELEMENT * 2);
        position += poolSize; // Add the size of the pool managed by this WordIndexReader.
        return position + bufferStartPosition;
    }

    /**
     * Returns the word at the given packed position.
     */
    final String getWordAtPacked(final int packed) {
        // First, look in the cache.
        String word = wordbyPackedIndex.get(packed);
        if (word != null) {
            return word;
        }
        // Read from disk, then cache the result.
        final int position = (packed >>> NUM_BITS_FOR_WORD_LENGTH) + numberOfWords*NUM_BYTES_FOR_INDEX_ELEMENT;
        int remaining = packed & ((1 << NUM_BITS_FOR_WORD_LENGTH) - 1);
        final ByteBuffer buffer = dictionary.buffer;
        buffer.position(bufferStartPosition + position);
        stringBuilder.setLength(0);
        while (--remaining >= 0) {
            int code = buffer.get() & 0xFF;
            if ((code & MASK_CHARACTER_INDEX_ON_TWO_BYTES) != 0) {
                assert remaining != 0;
                remaining--;
                code = (code & ~MASK_CHARACTER_INDEX_ON_TWO_BYTES) | ((buffer.get() & 0xFF) << 7);
            }
            code = encodingMap[code];
            final int start = code >>> Byte.SIZE;
            stringBuilder.append(charSequences, start, start + (code & 0xFF));
        }
        word = stringBuilder.toString();
        wordbyPackedIndex.put(packed, word);
        return word;
    }

    /**
     * Returns the word at the given index. The index is typically a value returned by
     * {@link #getWordIndex(String)}.
     *
     * @param  wordIndex Index of the word to search.
     * @return The word at the given index.
     * @throws IndexOutOfBoundsException If the given index is out of bounds.
     */
    public String getWordAt(final int wordIndex) throws IndexOutOfBoundsException {
        if (wordIndex < 0 || wordIndex >= numberOfWords) {
            throw new IndexOutOfBoundsException(String.valueOf(wordIndex));
        }
        return getWordAtPacked(dictionary.buffer.getInt(wordIndex*NUM_BYTES_FOR_INDEX_ELEMENT + bufferStartPosition));
    }

    /**
     * Searches the index of the given word. If no exact match is found, returns the
     * "insertion point" with all bits reversed (same convention than
     * {@link java.util.Arrays#binarySearch(Object[], Object)}).
     * <p>
     * If positive, the returned value can be given to {@link #getWordAt(int)}
     * in order to get the actual word at that index.
     *
     * @param  word The word to search.
     * @return The index of the given word, or the insertion point with all bits reversed.
     */
    public int getWordIndex(final String word) {
        int cachePos = 1;
        int low  = 0;
        int high = numberOfWords - 1;
        while (low <= high) {
            final int mid = (low + high) >>> 1;
            /*
             * Get the word at index 'mid'. We use a cache for the first 8 iterations, because those
             * iterations involve seeks over a large distance. After 8 iterations, the seek distances
             * will be much shorter, so the OS cache will hopefully be used more easily.
             */
            String midVal;
            if (cachePos >= 0 && cachePos < wordByIteration.length) {
                midVal = wordByIteration[cachePos];
                if (midVal == null) {
                    midVal = getWordAt(mid);
                    wordByIteration[cachePos] = midVal;
                }
                cachePos <<= 1;
            } else {
                midVal = getWordAt(mid);
            }
            /*
             * Now compare the word at the mid position with the word to search. Update the next index
             * (including the index of cached packed references) according the comparison result.
             */
            final int c = WordComparator.INSTANCE.compare(midVal, word);
            if (c == 0) {
                return mid;
            }
            if (c < 0) low = mid + 1;
            else     {high = mid - 1; cachePos |= 1;}
        }
        return ~low;
    }

    /**
     * Returns references to all entries associated to the word at the given index.
     *
     * @param  wordIndex Index of the word to search.
     * @return References to all entries associated to the word at the given index.
     */
    private int[] getEntryReferencesUsingWord(final int wordIndex) {
        final ByteBuffer buffer = dictionary.buffer;
        /*
         * Gets the position of the list of all entries associated to the word at the given index.
         * The 'position' value is packed: the first 3 bytes for the position, and the last byte
         * for the list length.
         */
        int position = buffer.getInt(wordIndex*NUM_BYTES_FOR_INDEX_ELEMENT + entryListRefStartPosition);
        final int length = position & 0xFF;
        position >>>= Byte.SIZE;
        buffer.position(dictionary.entryListsPoolStart + position*NUM_BYTES_FOR_ENTRY_POSITION);
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
        return references;
    }

    /**
     * Returns all entries at the given positions.
     *
     * @param references The references to entries.
     * @param length Number of valid elements in the references array.
     */
    private Entry[] getEntriesAt(final int[] references, final int length) {
        final Entry[] entries = new Entry[length];
        for (int i=0; i<length; i++) {
            entries[i] = dictionary.getEntryAt(references[i]);
        }
        return entries;
    }

    /**
     * Returns a collection of entries beginning by the given prefix. If no word begin by
     * the given prefix, then this method will look for shorter character sequences, until
     * a matching characters sequence is found.
     *
     * @param  prefix The prefix.
     * @param  prefixType On input the type of the prefix.
     *         On output, will contain the type of the prefix which has been actually used.
     * @param  allowReducedSearch If {@code true}, this method will reduce the number of entries
     *         if an exact match is found. For example if the user ask for "Internet", then this
     *         method will not return "Internet access", "Internet address", etc.
     * @return Entries beginning by the given prefix.
     */
    final Entry[] getEntriesUsingPrefix(final String prefix, final PrefixType prefixType, final boolean allowReducedSearch) {
        int wordIndex = getWordIndex(prefix);
        if (wordIndex < 0) {
            wordIndex = ~wordIndex;
            if (wordIndex == numberOfWords) {
                wordIndex--;
            }
        } else if (allowReducedSearch) {
            final int[] references = getEntryReferencesUsingWord(wordIndex);
            return getEntriesAt(references, references.length);
        }
        /*
         * Before to search for words in ascending order, we first need to look at little bit
         * backward in case a longer matching characters sequence exists. For example if we
         * search for "ABCD" in a dictionary containing only "ABCC" and "ABDD", then the
         * "entry >= 'ABCD'" condition while returns "ABDD". But the previous entry, "ABCC",
         * was a better march, so we need to check for it.
         */
        String candidate = getWordAt(wordIndex);
        int commonLength = commonPrefixLength(prefix, candidate);
        final boolean isAlphabetic = prefixType.isAlphabetic();
        if (isAlphabetic || commonLength != prefix.length()) {
            /*
             * We don't allow to skip this search for Latin characters, because we need to search
             * for words having a different case. For example the given prefix is all lower-cases
             * while the dictionary contains the word with upper-case characters, then the later
             * are located before the current 'wordIndex'.
             */
            while (wordIndex != 0) {
                if (!isAlphabetic && candidate.length() == commonLength) {
                    break; // The current word matches fully the begining of the prefix.
                }
                final String previous = getWordAt(wordIndex-1);
                final int cl = commonPrefixLength(prefix, previous);
                if (cl < commonLength) {
                    break;
                }
                candidate = previous;
                commonLength = cl;
                wordIndex--;
            }
            prefixType.update(prefix.substring(0, commonLength));
        }
        /*
         * Now build the list of entries by scanning all matching character sequences
         * in ascending order. As a special case, we will skip this search if the word
         * that we found above matches exactly the begining of the string to search,
         * because we will not find a better match.
         */
        int[] references = getEntryReferencesUsingWord(wordIndex);
        int numEntries = references.length;
        int minLength = candidate.length();
        if (commonLength != minLength || !allowReducedSearch) {
            Arrays.sort(references);
            while (++wordIndex != numberOfWords) {
                candidate = getWordAt(wordIndex);
                if (commonPrefixLength(prefix, candidate) != commonLength) {
                    break; // The next word is no longer a good match - stop.
                }
                final int length = candidate.length();
                if (length > minLength) {
                    if (allowReducedSearch) {
                        continue; // Give precedence to shortest words.
                    }
                } else {
                    minLength = length;
                }
                for (int more : getEntryReferencesUsingWord(wordIndex)) {
                    int insertAt = Arrays.binarySearch(references, 0, numEntries, more);
                    if (insertAt < 0) {
                        insertAt = ~insertAt;
                        if (numEntries == references.length) {
                            references = Arrays.copyOf(references, Math.max(8, numEntries*2));
                        }
                        System.arraycopy(references, insertAt, references, insertAt+1, numEntries-insertAt);
                        references[insertAt] = more;
                        numEntries++;
                    }
                }
            }
        }
        return getEntriesAt(references, numEntries);
    }

    /**
     * Returns the length of the prefix which is also found at the beginning of the given candidate.
     * The comparison is case-insensitive.
     */
    private static int commonPrefixLength(final String prefix, final String candidate) {
        final int n1 = prefix.length();
        final int n2 = candidate.length();
        int i1=0, i2=0;
        while (i1 < n1 && i2 < n2) {
            final int c1 = prefix   .codePointAt(i1);
            final int c2 = candidate.codePointAt(i2);
            if (!WordComparator.equals(c1, c2)) {
                break;
            }
            i1 += Character.charCount(c1);
            i2 += Character.charCount(c2);
        }
        return i1;
    }
}
