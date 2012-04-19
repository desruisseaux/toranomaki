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

import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.BinaryData;


/**
 * Character encoding determined from the frequencies of character sequences.
 *
 * @author Martin Desruisseaux
 */
class WordEncoder extends BinaryData {
    /**
     * Maximal length of character sequences to analyze. This is based on empirical trial.
     */
    private static final int MAX_SEQUENCE_LENGTH = 4;

    /**
     * {@code true} for adding Japanese words, or {@code false} for adding senses.
     */
    final boolean isAddingJapanese;

    /**
     * On construction, this will hold the frequencies of a few characters.
     * After construction, this will hold the codes allocated to characters.
     */
    private final Map<String,Integer> encodingMap;

    /**
     * Creates a new encoder from the frequencies of character sequences in the given entries.
     *
     * @param entries  The entries for which to create en encoder.
     * @param japanese {@code true} for adding Japanese words, or {@code false} for adding senses.
     */
    public WordEncoder(final Collection<Entry> entries, final boolean japanese) {
        isAddingJapanese = japanese;
        encodingMap = new HashMap<>(MAX_SEQUENCE_LENGTH * entries.size());
        /*
         * Computes the frequencies of character sequences in the given entries.
         */
        for (final Entry entry : entries) {
            if (japanese) {
                boolean isKanji = false;
                do {
                    final int count = entry.getCount(isKanji);
                    for (int i=0; i<count; i++) {
                        countCharSequenceFrequencies(entry.getWord(isKanji, i));
                    }
                } while ((isKanji = !isKanji) == true);
            } else {
                for (final Sense sense : entry.getSenses()) {
                    countCharSequenceFrequencies(sense.meaning);
                }
            }
        }
        /*
         * Sorts all character sequences by decreasing frequencies.
         * We compare the words for "cost" benefit. The most interesting strings are sorted first.
         * Note: current implementation is slightly inaccurate, as we don't take in account the fact
         * that the 128 most frequent characters will be stored on a single bytes instead than 2. At
         * this point, we don't know yet which characters will be on a single byte.
         */
        final Map<String,Integer> encodingMap = this.encodingMap;
        final String[] sequences = encodingMap.keySet().toArray(new String[encodingMap.size()]);
        Arrays.sort(sequences, new Comparator<String>() {
            @Override public final int compare(final String o1, final String o2) {
                return encodingMap.get(o2) * o2.length() -
                       encodingMap.get(o1) * o1.length();
            }
        });
        /*
         * First, make sure that every single character are unconditionnaly included,
         * no matter their rank. Their code will be determined later.
         */
        encodingMap.clear();
        for (final String candidate : sequences) {
            if (candidate.length() == 1) {
                encodingMap.put(candidate, null);
            }
        }
        /*
         * Now select the most common sequences, which will be encoded using only ony byte.
         * Note that some single characters may not be included in those preferred slots,
         * because they appear in very rare occasion.
         */
        int index = Math.min(MASK_CHARACTER_INDEX_ON_TWO_BYTES, sequences.length);
        for (int i=0; i<index; i++) {
            final String sequence = sequences[i];
            if (encodingMap.put(sequence, i) != null) {
                throw new AssertionError(sequence);
            }
        }
        /*
         * Store remaining sequences in decreasing frequency order, until all slots are
         * occupied. Those sequences will be stored using two bytes.
         */
        for (int i=index; i<sequences.length; i++) {
            if (encodingMap.size() == 0x8000) {
                break;
            }
            final String sequence = sequences[i];
            if (!canEncodeOnTwoBytes(sequence)) {
                if (encodingMap.put(sequence, encodeTwoBytes(index)) != null) {
                    throw new AssertionError(sequence);
                }
                index++;
            }
        }
        /*
         * Compute the codes for remaining single characters. We reserved a slot for those
         * characters before to assign a number to the most frequent sequences. Now assign
         * the actual value to those reserved slots.
         */
        for (final Map.Entry<String,Integer> entry : encodingMap.entrySet()) {
            if (entry.getValue() == null) {
                entry.setValue(encodeTwoBytes(index++));
            }
        }
    }

    /**
     * Adds the given words to the statistic computation.
     * This method is invoked by the constructor only.
     */
    private void countCharSequenceFrequencies(final String word) {
        final int length = word.length();
        for (int j=1; j<=MAX_SEQUENCE_LENGTH; j++) {
            for (int i=j; i<=length; i++) {
                final String sequence = word.substring(i-j, i);
                final Integer count = encodingMap.put(sequence, 1);
                if (count != null) {
                    encodingMap.put(sequence, count+1);
                }
            }
        }
    }

    /**
     * Encodes the given index on two bytes. We "insert" the bit 1 at the MASK_TWO_BYTES location.
     * All bits ahead of that location are shifted to the left.
     */
    private static int encodeTwoBytes(final int index) {
        int code = index & ~(MASK_CHARACTER_INDEX_ON_TWO_BYTES - 1); // Higher bits.
        code <<= 1;
        code |= (index & (MASK_CHARACTER_INDEX_ON_TWO_BYTES - 1)); // Lower bits
        code |= MASK_CHARACTER_INDEX_ON_TWO_BYTES;
        assert (code > MASK_CHARACTER_INDEX_ON_TWO_BYTES) && (code & 0xFFFF0000) == 0 : index;
        assert decodeTwoBytes(code) == index : index;
        return code;
    }

    /**
     * The reverse of {@link #decodeTwoBytes(int)}.
     */
    private static int decodeTwoBytes(int code) {
        final int index = code & (MASK_CHARACTER_INDEX_ON_TWO_BYTES - 1); // Higher bits.
        code = (code & ~((MASK_CHARACTER_INDEX_ON_TWO_BYTES << 1) - 1)) >>> 1;
        return index | code;
    }

    /**
     * Returns {@code true} if the given string can be encoded efficiently with the existing
     * mapping.
     */
    private boolean canEncodeOnTwoBytes(final String sequence) {
        for (int i=sequence.length(); --i>=1;) {
            Integer code = encodingMap.get(sequence.substring(0, i));
            if (code != null && (code & MASK_CHARACTER_INDEX_ON_TWO_BYTES) == 0) {
                code = encodingMap.get(sequence.substring(i));
                if (code != null && (code & MASK_CHARACTER_INDEX_ON_TWO_BYTES) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Encodes the given word to the given buffer.
     */
    public final void encode(final String word, final ByteBuffer buffer) {
        final int length = word.length();
next:   for (int i=0; i<length;) {
            for (int j=Math.min(MAX_SEQUENCE_LENGTH, length-i); j>=1; j--) {
                final Integer code = encodingMap.get(word.substring(i, i+j));
                if (code != null) {
                    final int n = code;
                    buffer.put((byte) n);
                    if ((n & MASK_CHARACTER_INDEX_ON_TWO_BYTES) != 0) {
                        buffer.put((byte) (n >>> Byte.SIZE));
                    }
                    i += j;
                    continue next;
                }
            }
            throw new IllegalArgumentException("Can't encode " + word);
        }
    }

    /**
     * Writes the table of character encoding. This table is necessary in order to allow
     * the reader to decode the characters back to the original strings. The format is:
     * <p>
     * <ul>
     *   <li>Length of the character sequence pool, as an {@code int}.</li>
     *   <li>Number of character sequences, as an unsigned {@code short}.</li>
     *   <li>Position and length of each character sequence, packed in {@code int}.</li>
     *   <li>All character sequences encoded in UTF-8 or UTF-16.</li>
     * </ul>
     *
     * @param out The channel where to write.
     * @param buffer A temporary buffer to use.
     */
    public final void writeEncodingTable(final WritableByteChannel out, final ByteBuffer buffer) throws IOException {
        /*
         * Get the list of all character sequences sorted by their index.
         */
        final String[] sequences = new String[encodingMap.size()];
        for (final Map.Entry<String,Integer> entry : encodingMap.entrySet()) {
            final int index = decodeTwoBytes(entry.getValue());
            if (sequences[index] != null) {
                throw new IllegalStateException("Key collision: " + index);
            }
            sequences[index] = entry.getKey();
        }
        /*
         * Creates a character pools containing only the sequences that are not
         * substring of an other sequences.
         */
        final String pool;
        if (true) {
            final Set<String> substrings = new HashSet<>(sequences.length);
            for (final String sequence : sequences) {
                final int length = sequence.length();
                for (int j=0; j<length; j++) {
                    final int stop = (j == 0) ? length-1 : length;
                    for (int k=j+1; k<=stop; k++) {
                        substrings.add(sequence.substring(j,k));
                    }
                }
            }
            final StringBuilder builder = new StringBuilder();
            for (final String sequence : sequences) {
                if (!substrings.contains(sequence)) {
                    builder.append(sequence);
                }
            }
            pool = builder.toString();
        }
        /*
         * Computes the length of the character pool.
         */
        final ByteBuffer bytes = ByteBuffer.wrap(pool.getBytes(isAddingJapanese ? JAPAN_ENCODING : LATIN_ENCODING));
        buffer.putInt(bytes.limit());
        /*
         * Write the number of character sequences, then the position and length of each
         * sequence (packed in a single 'int') in the character pool, then the character pool.
         */
        assert sequences.length <= 0xFFFF;
        buffer.putShort((short) sequences.length);
        for (final String sequence : sequences) {
            int pos = pool.indexOf(sequence);
            assert (pos >= 0) : sequence;
            pos = (pos << Byte.SIZE) | sequence.length();
            buffer.putInt(pos);
            // Test after addition because we assume that the buffer was big enough
            // for the first 'int', and we want to ensure that there is enough room
            // for the 'int' that we will add after this loop.
            if (buffer.remaining() < Integer.SIZE / Byte.SIZE) {
                writeFully(buffer, out);
            }
        }
        writeFully(buffer, out);
        do out.write(bytes);
        while (bytes.hasRemaining());
    }

    /**
     * {@linkplain ByteBuffer#flip() Flips} the given buffer, then writes fully its content
     * to the given channel. After the write operation, the buffer is cleared for reuse.
     */
    static void writeFully(final ByteBuffer buffer, final WritableByteChannel out) throws IOException {
        buffer.flip();
        do out.write(buffer);
        while (buffer.hasRemaining());
        buffer.clear();
    }
}
