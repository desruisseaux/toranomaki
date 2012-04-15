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
package fr.toranomaki.edict.writer;

import java.util.Map;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Collection;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;


/**
 * Writes an array of {@link String} instances in a binary format, together with an index.
 * The file created by this object can be read with {@link WordIndexReader}.
 *
 * @author Martin Desruisseaux
 */
final class WordIndexWriter extends WordEncoder {
    /**
     * All words given to the constructor. The keys are the words to write in the file.
     * The values are packed index and length of byte sequences in the file written by
     * {@link #write(Path)}.
     */
    private final Map<String,EncodedWord> encodedWords;

    /**
     * The buffer containing encoded bytes. Will also be used as a buffer for writing
     * to the byte channel. The buffer capacity most be a multiple of the {@code int}
     * size.
     */
    private final ByteBuffer buffer;

    /**
     * Creates a new writer which will create the dictionary files for a list of words.
     * This constructor will performs all needed computation immediately.
     *
     * @param entries  The entries for which to create en encoder.
     * @param japanese {@code true} for adding Japanese words, or {@code false} for adding senses.
     * @param buffer   A buffer to use. Its content will be overwritten.
     */
    public WordIndexWriter(final Collection<Entry> entries, final boolean japanese, final ByteBuffer buffer) {
        super(entries, japanese);
        assert buffer.capacity() % ELEMENT_SIZE == 0;
        this.buffer  = buffer;
        encodedWords = new LinkedHashMap<>(2 * entries.size());
        /*
         * Sets the entries to write. The 'wordFragments' map will contains portion of encoded
         * words. This is used in order to detect the words that can be encoded as substrings
         * of an existing word. The values in this map are identical to the keys.
         */
        final SortedMap<EncodedWord,EncodedWord> wordFragments = new TreeMap<>();
        for (final Entry entry : entries) {
            if (japanese) {
                boolean isKanji = false;
                do {
                    final int count = entry.getCount(isKanji);
                    for (int i=0; i<count; i++) {
                        addWord(entry.getWord(isKanji, i), wordFragments);
                    }
                } while ((isKanji = !isKanji) == true);
            } else {
                for (final Sense sense : entry.getSenses()) {
                    addWord(sense.meaning, wordFragments);
                }
            }
        }
        /*
         * Searches for any byte sequences that are subarray of another byte sequence.
         * The addWord(String, ...) method already handled the cases where a word is identical to
         * the beginning of another word. This method handles the cases where a word is identical
         * to the ending of another word.
         */
        for (EncodedWord encoded : encodedWords.values()) {
            while (encoded.isSubstringOf != null) {
                encoded = encoded.isSubstringOf;
            }
            EncodedWord candidate = encoded;
search:     for (final EncodedWord next : wordFragments.tailMap(encoded).values()) {
                switch (next.compareTo(candidate)) {
                    case 0:  continue;     // Look for the next word.
                    case 1:  break;        // Found a longer word.
                    case 2:  break search; // Bytes are different.
                    default: throw new AssertionError(candidate);
                }
                encoded.isSubstringOf = candidate = next;
            }
        }
    }

    /**
     * Encodes the given word and adds it to the internal map of words. This method will also
     * add all substrings beginning in the same way but ending at different lengths, in order
     * to allow us to detect later which words are substrings of other words.
     */
    private void addWord(final String word, final SortedMap<EncodedWord,EncodedWord> wordFragments) {
        buffer.clear();
        encode(word, buffer);
        final EncodedWord encoded = new EncodedWord(word, Arrays.copyOf(buffer.array(), buffer.position()));
        EncodedWord old = encodedWords.put(word, encoded);
        if (old != null) {
            // Restore the previous value and stop.
            encodedWords.put(word, old);
        } else {
            old = wordFragments.put(encoded, encoded);
            if (old != null) {
                // The word we just added is a substring of a previous word.
                // Restore the previous entry and keep the reference to the
                // enclosing word.
                if (old.word == null) {
                    old.word = word;
                }
                wordFragments.put(old, old);
                encodedWords.put(word, old);
            } else {
                for (int i=encoded.bytes.length; --i>=1;) {
                    // We overwrite any existing fragments, because the word that we
                    // added is longer than any previously added word, otherwise the
                    // wordFragments.put(...) call we did before the loop would have
                    // returned a non-null value.
                    final EncodedWord fragment = new EncodedWord(encoded, i);
                    old = wordFragments.put(fragment, fragment);
                    if (old != null && old.word != null) {
                        fragment.word = old.word;
                        encodedWords.put(old.word, fragment);
                    }
                }
            }
        }
        assert (old = encodedWords.get(word)) != null;
        assert word.equals(old.word)       : old + " not equals to " + word;
        assert old.compareTo(encoded) == 0 : old + " not equals to " + encoded;
    }

    /**
     * Writes the header to the given channel.
     * This method writes:
     * <p>
     * <ul>
     *   <li>The {@linkplain #MAGIC_NUMBER magic number} as a {@code int}.</li>
     *   <li>Number of words, as an {@code int}.</li>
     *   <li>The length of the pool of bytes, as an {@code int}.</li>
     *   <li>The encoding used in the pool of bytes, as documented in
     *       {@link #writeEncodingTable(WritableByteChannel, ByteBuffer)}.</li>
     * </ul>
     *
     * @param  out The output channel.
     * @return A map of words associated with their position in the file.
     */
    public WordTable writeHeader(final WritableByteChannel out) throws IOException {
        /*
         * Determine what would be the position of each words and write every words to the file.
         * The words must be sorted by alphebetical order in order to allow the index to work.
         */
        int position = 0;
        final WordTable result = new WordTable(encodedWords.keySet());
        for (final String word : result.words) {
            final EncodedWord encoded = encodedWords.get(word);
            if (encoded.isSubstringOf == null) {
                encoded.position = position;
                position += encoded.length;
            }
            // Do not update the (isSubstringOf != null) cases in this loop,
            // because we need all (isSubstringOf == null) cases to be resolved first.
        }
        buffer.clear();
        buffer.putInt(MAGIC_NUMBER);
        buffer.putInt(encodedWords.size());
        buffer.putInt(position);
        writeEncodingTable(out, buffer);
        return result;
    }

    /**
     * Writes the index after the header. This method write the portion of the file which will
     * be mapped by a direct NIO buffer at reading time.
     * <p>
     * This method writes:
     * <ul>
     *   <li>Packed references to the encoded words as {@code int} numbers where the first bits are
     *       the index of the first byte to use in the pool (0 is the first byte after all packed
     *       references), and the last {@value #NUM_BITS_FOR_WORD_LENGTH} bits are the number of bytes
     *       to read from the pool.</li>
     *   <li>A pool of bytes which represent the encoded words.</li>
     * </ul>
     *
     * @param  wordTable The table produced by {@link #writeHeader(WritableByteChannel, boolean)}.
     * @param  file The output channel.
     */
    public void writeIndex(final WordTable wordTable, final WritableByteChannel out) throws IOException {
        /*
         * Now process to the actual writing. This method will also computes the final
         * "packed" position as a side-effect of this process.
         */
        for (int i=0; i<wordTable.words.length; i++) {
            final String word = wordTable.words[i];
            EncodedWord encoded = encodedWords.get(word);
            assert word.equals(encoded.word) : encoded;
            // Get the expected position in the file, taking in account the
            // case where this word is a substring of an existing word.
            final int length = encoded.length;
            int offset = 0;
            while (encoded.isSubstringOf != null) {
                offset += encoded.offset();
                encoded = encoded.isSubstringOf;
            }
            final int position = encoded.position + offset;
            // Check against overflow.
            if (position >= (1 << (Integer.SIZE - NUM_BITS_FOR_WORD_LENGTH))) {
                throw new IOException("Too many bytes: " + position);
            }
            if (length >= (1 << NUM_BITS_FOR_WORD_LENGTH)) {
                throw new IOException("String is too long: " + word + " (" + length + " bytes)");
            }
            // Save the packed value.
            final int packed = (position << NUM_BITS_FOR_WORD_LENGTH) | length;
            wordTable.positions[i] = packed;
            if (!buffer.putInt(packed).hasRemaining()) {
                writeFully(buffer, out);
            }
        }
        // After the index, write the actual encoded words.
        for (final String word : wordTable.words) {
            final EncodedWord encoded = encodedWords.remove(word);
            if (encoded.isSubstringOf == null) {
                if (buffer.remaining() < encoded.bytes.length) {
                    writeFully(buffer, out);
                }
                buffer.put(encoded.bytes);
            }
        }
        writeFully(buffer, out);
        assert encodedWords.isEmpty() : encodedWords;
    }
}
