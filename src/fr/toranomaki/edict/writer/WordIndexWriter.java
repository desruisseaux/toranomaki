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
import java.util.Set;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Arrays;
import java.io.IOException;
import java.io.CharConversionException;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.WordComparator;
import fr.toranomaki.edict.WordIndexReader;

import static fr.toranomaki.edict.WordIndexReader.*;


/**
 * Writes an array of {@link String} instances in a binary format, together with an index.
 * The methods in this class shall be invoked in the following order:
 * <p>
 * <ol>
 *   <li>{@link #initialize(boolean)}</li>
 *   <li>{@link #add(Entry)} for every entries to add.</li>
 *   <li>{@link #write(Path)} for creating the binary file.</li>
 * </ol>
 * <p>
 * The file created by this object can be read with {@link WordIndexReader}.
 *
 * @author Martin Desruisseaux
 */
final class WordIndexWriter {
    /**
     * Set to {@code true} for verifying the binary file after writing.
     */
    private static final boolean VERIFY = false;

    /**
     * {@code true} for adding Japanese words, or {@code false} for adding senses.
     *
     * @see #initialize(boolean)
     */
    private boolean isAddingJapanese;

    /**
     * The encoder to use for creating the sequence of bytes from a word.
     *
     * @see #initialize(boolean)
     */
    private CharsetEncoder encoder;

    /**
     * The buffer where to put a word before to encode it.
     */
    private final CharBuffer charBuffer;

    /**
     * The buffer containing encoded bytes. Will also be used as a buffer for writing
     * to the byte channel. The buffer capacity most be a multiple of the {@code int}
     * size.
     */
    private final ByteBuffer buffer;

    /**
     * All words given to the constructor. The keys are the words to write in the file.
     * The values are packed index and length of byte sequences in the file written by
     * {@link #write(Path)}.
     */
    private final Map<String,EncodedWord> encodedWords;

    /**
     * Encoded words, or portion of encoded words. This is used in order to detect
     * the words that can be encoded as substrings of an existing word. The values
     * in this map are identical to the keys.
     */
    private final SortedMap<EncodedWord,EncodedWord> wordFragments;

    /**
     * For statistics purpose.
     */
    private int countBytesTotal, countBytesDistinctWords, countBytesActual;

    /**
     * The encoded representation of a word. The natural ordering is determined by the
     * bytes sequence, by iterating over the bytes <strong>in reverse order</strong>
     * (from the last bytes to the first one). We use reverse order because we will
     * store in the {@link WordIndexWriter#wordFragments} map the same bytes with
     * more and more tail bytes omitted. So the map will contains many words with the
     * same prefix, which is conform to the practice found in many language where many
     * words have the same prefix but different suffixes.
     */
    private static final class EncodedWord implements Comparable<EncodedWord> {
        /** The original word, or {@code null} if this object is for a substring. */
        String word;

        /** The encoded word. */
        final byte[] bytes;

        /** Number of valid bytes in the {@link #bytes} array. Extra bytes are ignored. */
        final short length;

        /** Non-null if an other instance contains the same bytes sequence. */
        EncodedWord isSubstringOf;

        /** Position in the file, for {@link WordIndexWriter#write(Path)} internal usage. */
        int position;

        /**
         * Creates a new instance for the given encoded word.
         */
        EncodedWord(final String word, final byte[] bytes) {
            this.word     = word;
            this.bytes    = bytes;
            this.length   = (short) bytes.length;
            isSubstringOf = null;
        }

        /**
         * Creates a new instance which is a substring of the given instance.
         */
        EncodedWord(final EncodedWord enclosing, final int length) {
            this.word     = null;
            this.bytes    = enclosing.bytes;
            this.length   = (short) length;
            isSubstringOf = enclosing;
        }

        /**
         * Returns the first valid byte. This is always zero, except for the instances
         * which have been processed by {@link WordIndexWriter#shareCommonBytes()}.
         */
        int offset() {
            return isSubstringOf.length - bytes.length;
        }

        /**
         * Compares for order. The {@code EncodedWord} instances must be sorted by this criterion
         * before to be saved on the file, in order to allow the binary search to work.
         *
         * @return 0 if the arrays are equal;
         *         -1 or +1 if all bytes match but one array is shorter than the other;
         *         -2 or +2 if at least one byte is different.
         */
        @Override
        public int compareTo(final EncodedWord other) {
            final byte[] tb = this .bytes; int ti = this .length;
            final byte[] ob = other.bytes; int oi = other.length;
            while ((--ti >= 0) & (--oi >= 0)) { // Really &, not &&
                final int c = tb[ti] - ob[oi];
                if (c != 0) {
                    return (c < 0) ? -2 : 2;
                }
            }
            if (ti == oi) return 0;
            return (ti < oi) ? -1 : 1;
        }

        /**
         * Returns a string representation for debugging purpose.
         */
        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder(20);
            buffer.append('[');
            if (word != null) {
                buffer.append('"').append(word).append("\": ");
            }
            buffer.append(length).append(" bytes");
            if (isSubstringOf != null) {
                buffer.append(" of ").append(isSubstringOf);
            }
            return buffer.append(']').toString();
        }
    }

    /**
     * Creates a new writer which will create the dictionary files for a list of words.
     * The {@link #initialize(boolean)} method must be invoked before this writer can be used.
     */
    public WordIndexWriter(final int numEntries) {
        charBuffer    = CharBuffer.allocate(1 << NUM_BITS_FOR_LENGTH);
        buffer        = ByteBuffer.allocate(1024 * ELEMENT_SIZE);
        encodedWords  = new LinkedHashMap<>(numEntries + (numEntries / 4));
        wordFragments = new TreeMap<>();
    }

    /**
     * Prepares this writer for the addition of words in the given language.
     *
     * @param japanese {@code true} for adding Japanese words, or {@code false} for adding senses.
     */
    public void initialize(final boolean japanese) {
        assert encodedWords.isEmpty() && wordFragments.isEmpty();
        isAddingJapanese = japanese;
        encoder = Charset.forName(japanese ? JAPAN_ENCODING : LATIN_ENCODING).newEncoder();
    }

    /**
     * Adds the given entry in the list of entries to write.
     *
     * @param  entry The entry to write.
     * @throws CharConversionException If the given word can not be encoded.
     */
    public void add(final Entry entry) throws CharConversionException {
        if (isAddingJapanese) {
            boolean isKanji = false;
            do {
                final int count = entry.getCount(isKanji);
                for (int i=0; i<count; i++) {
                    addWord(entry.getWord(isKanji, i));
                }
            } while ((isKanji = !isKanji) == true);
        } else {
            for (final Sense sense : entry.getSenses()) {
                addWord(sense.meaning);
            }
        }
    }

    /**
     * Encodes the given word and adds it to the internal map of words. This method will also
     * add all substrings beginning in the same way but ending at different lengths, in order
     * to allow us to detect later which words are substrings of other words.
     */
    private void addWord(final String word) throws CharConversionException {
        encoder.reset();
        buffer.clear();
        charBuffer.clear();
        charBuffer.put(word).flip();
        CoderResult result = encoder.encode(charBuffer, buffer, true);
        if (result == CoderResult.UNDERFLOW) {
            result = encoder.flush(buffer);
            if (result == CoderResult.UNDERFLOW) {
                final EncodedWord encoded = new EncodedWord(word, Arrays.copyOf(buffer.array(), buffer.position()));
                EncodedWord old = encodedWords.put(word, encoded);
                countBytesTotal += encoded.length;
                if (old != null) {
                    // Restore the previous value and stop.
                    encodedWords.put(word, old);
                } else {
                    countBytesDistinctWords += encoded.length;
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
                return;
            }
        }
        throw new CharConversionException("Encoding error: " + result);
    }

    /**
     * Searches for any byte sequences that are subarray of another byte sequence.
     * The {@link #add(Entry)} method already handled the cases where a word is identical to
     * the beginning of another word. This method handles the cases where a word is identical
     * to the ending of another word.
     */
    private void shareCommonBytes() {
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
        wordFragments.clear(); // Not needed anymore, so let GC do its work.
    }

    /**
     * A map of words associated to their position in the binary file.
     * See {@link WordIndexWriter#write(Path)} for an explanation of the
     * packed {@linkplain #positions} format.
     */
    static final class Result {
        /** The words, sorted in alphabetical order. */
        final String[] words;

        /** The packed position of each words in the file. */
        final int[] positions;

        /** Prepares a new result for the given collection of words. */
        Result(final Set<String> words) {
            this.words = words.toArray(new String[words.size()]);
            positions = new int[this.words.length];
            Arrays.sort(this.words, WordComparator.INSTANCE);
        }
    }

    /**
     * Writes the pool of all possible sequences of characters in the given file.
     * The file format is:
     * <p>
     * <ol>
     *   <li>The {@linkplain #MAGIC_NUMBER magic number}, as a {@code long}.</li>
     *   <li>Number of words, as an {@code int}.</li>
     *   <li>The length of the pool of bytes, as an {@code int}.</li>
     *   <li>Packed references to the encoded words are {@code int} numbers where the first bits are
     *       the index of the first byte to use in the pool (0 is the first byte after all packed
     *       references), and the last {@value #NUM_BITS_FOR_LENGTH} bits are the number of bytes
     *       to read from the pool.</li>
     *   <li>A pool of bytes which represent the encoded words.</li>
     * </ol>
     *
     * @param  file The output file.
     * @return A map of words associated with their position in the file.
     */
    public Result write(final Path file) throws IOException {
        shareCommonBytes();
        /*
         * Determine what would be the position of each words and write every words to the file.
         * The words must be sorted by alphebetical order in order to allow the index to work.
         */
        int position = 0;
        final Result result = new Result(encodedWords.keySet());
        for (final String word : result.words) {
            final EncodedWord encoded = encodedWords.get(word);
            if (encoded.isSubstringOf == null) {
                encoded.position = position;
                position += encoded.length;
            }
            // Do not update the (isSubstringOf != null) cases in this loop,
            // because we need all (isSubstringOf == null) cases to be resolved first.
        }
        /*
         * Now process to the actual writing. This method will also computes the final
         * "packed" position as a side-effect of this process.
         */
        buffer.order(ByteOrder.nativeOrder()).clear();
        buffer.putLong(MAGIC_NUMBER);
        buffer.putInt(encodedWords.size());
        buffer.putInt(position);
        countBytesActual += position;
        try (FileChannel out = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i=0; i<result.words.length; i++) {
                final String word = result.words[i];
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
                position = encoded.position + offset;
                // Check against overflow.
                if (position >= (1 << (Integer.SIZE - NUM_BITS_FOR_LENGTH))) {
                    throw new IOException("Too many bytes: " + position);
                }
                if (length >= (1 << NUM_BITS_FOR_LENGTH)) {
                    throw new IOException("String is too long: " + word + " (" + length + " bytes)");
                }
                // Save the packed value.
                final int packed = (position << NUM_BITS_FOR_LENGTH) | length;
                result.positions[i] = packed;
                if (!buffer.putInt(packed).hasRemaining()) {
                    writeFully(buffer, out);
                }
            }
            // After the index, write the actual encoded words.
            for (final String word : result.words) {
                final EncodedWord encoded = encodedWords.remove(word);
                if (encoded.isSubstringOf == null) {
                    if (buffer.remaining() < encoded.bytes.length) {
                        writeFully(buffer, out);
                    }
                    buffer.put(encoded.bytes);
                }
            }
            writeFully(buffer, out);
        }
        assert encodedWords.isEmpty() : encodedWords;
        /*
         * At this point, we are done.  If verification is enabled, create a new reader and
         * verify every words that we wrote. This is executed in testing phase to make sure
         * that we can read fully what we just wrote.
         */
        if (VERIFY) {
            final String[] words = result.words.clone();
            Collections.shuffle(Arrays.asList(words));
            final WordIndexReader reader = new WordIndexReader(file, isAddingJapanese);
            for (int i=0; i<words.length; i++) {
                final String word = words[i];
                final Entry entry = reader.search(word);
                if (!word.equals(entry.getWord(false, 0))) {
                    throw new IOException("Verification failed for word \"" + word + "\" at index " + i);
                }
            }
        }
        return result;
    }

    /**
     * {@linkplain ByteBuffer#flip() Flips} the given buffer, then writes fully its content
     * to the given channel. After the write operation, the buffer is cleared for reuse.
     */
    private static void writeFully(final ByteBuffer buffer, final WritableByteChannel out) throws IOException {
        buffer.flip();
        do out.write(buffer);
        while (buffer.hasRemaining());
        buffer.clear();
    }

    /**
     * Prints a few statistics for debugging purpose.
     */
    public void printStatistics() {
        System.out.println("Total number of bytes:  " + countBytesTotal          / (1024*1024f) + " Mb");
        System.out.println("Distinct words only:    " + countBytesDistinctWords  / (1024*1024f) + " Mb");
        System.out.println("Actual number of bytes: " + countBytesActual         / (1024*1024f) + " Mb");
    }
}
