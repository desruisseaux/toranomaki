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
import java.util.HashMap;
import java.util.Comparator;
import java.util.Arrays;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;

import static fr.toranomaki.edict.WordIndexReader.*;


/**
 * Determines the frequency of a few character sequences. This is used for computing
 * an encoding to use in the binary files to be written by {@link WordIndexWriter}.
 *
 * @author Martin Desruisseaux
 */
final class FrequencyAnalyzer extends SectionWriter implements Comparator<String> {
    /**
     * Maximal length of character sequences to analyze. This is based on empirical trial.
     */
    private static final int MAX_CHARACTERS = (1 << NUM_BITS_FOR_CHAR_LENGTH);

    /**
     * The mask indicating that a character is stored on two bytes rather than one.
     * We store the 128 most common characters on 1 byte, and the remaining on two
     * bytes.
     */
    private static final int MASK_TWO_BYTES = 0x80;

    /**
     * On construction, this will hold the frequencies of a few characters.
     * After construction, this will hold the codes allocated to characters.
     */
    private final Map<String,Integer> encodingMap;

    /**
     * {@code true} if {@link #computeEncoding()} has been invoked.
     */
    private boolean encodingComputed;

    /**
     * Prepares the analyzing of frequencies of the given approximative amount of entries.
     * The {@link #addEntries(Iterable, boolean)} method shall be invoked after this constructor.
     */
    FrequencyAnalyzer(final int count) {
        encodingMap = new HashMap<>(count*2);
    }

    /**
     * Analyzes the frequencies of the given collection of entries. This method can be
     * invoked more than once. Statistics will accumulate, unless {@link #clear()} has
     * been invoked.
     */
    public void addEntries(final Iterable<Entry> entries, final boolean japanese) {
        if (encodingComputed) {
            throw new IllegalStateException("Invoke clear() before to add new entries.");
        }
        isAddingJapanese = japanese;
        for (final Entry entry : entries) {
            if (japanese) {
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
    }

    /**
     * Adds the given words to the static computation.
     */
    private void addWord(final String word) {
        final int length = word.length();
        for (int j=1; j<=MAX_CHARACTERS; j++) {
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
     * Compares the given words for cost benefit. The most interesting strings are sorted first.
     * Note: current implementation is slightly inaccurate, as we don't take in account the fact
     * that the 128 most frequent characters will be stored on a single bytes instead than 2. At
     * this point, we don't know yet which characters will be on a single byte.
     */
    @Override
    public int compare(final String o1, final String o2) {
        return encodingMap.get(o2) * o2.length() -
               encodingMap.get(o1) * o1.length();
    }

    /**
     * Computes the encoding table. This method should be invoked after all entries
     * have been {@linkplain #addEntries(Iterable, boolean) added}. No new entries
     * can be added after this method call, unless {@link #clear()} has been invoked.
     */
    public void computeEncoding() {
        if (encodingComputed) {
            throw new IllegalStateException("computeEncoding() has already been invoked.");
        }
        encodingComputed = true;
        final String[] sequences = encodingMap.keySet().toArray(new String[encodingMap.size()]);
        Arrays.sort(sequences, this); // Still need the frequencies map at this point.
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
        int index = Math.min(MASK_TWO_BYTES, sequences.length);
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
     * Encodes the given index on two bytes. We "insert" the bit 1 at the MASK_TWO_BYTES location.
     * All bits ahead of that location are shifted to the left.
     */
    private static int encodeTwoBytes(final int index) {
        int code = index & ~(MASK_TWO_BYTES - 1); // Higher bits.
        code <<= 1;
        code |= (index & (MASK_TWO_BYTES - 1)); // Lower bits
        code |= MASK_TWO_BYTES;
        assert (code > MASK_TWO_BYTES) && (code & 0xFFFF0000) == 0 : index;
        assert decodeTwoBytes(code) == index : index;
        return code;
    }

    /**
     * The reverse of {@link #decodeTwoBytes(int)}.
     */
    private static int decodeTwoBytes(int code) {
        final int index = code & (MASK_TWO_BYTES - 1); // Higher bits.
        code = (code & ~((MASK_TWO_BYTES << 1) - 1)) >>> 1;
        return index | code;
    }

    /**
     * Returns {@code true} if the given string can be encoded efficiently with the existing
     * mapping.
     */
    private boolean canEncodeOnTwoBytes(final String sequence) {
        for (int i=sequence.length(); --i>=1;) {
            Integer code = encodingMap.get(sequence.substring(0, i));
            if (code != null && (code & MASK_TWO_BYTES) == 0) {
                code = encodingMap.get(sequence.substring(i));
                if (code != null && (code & MASK_TWO_BYTES) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Encodes the given word to the given buffer.
     */
    public void encode(final String word, final ByteBuffer buffer) {
        if (!encodingComputed) {
            throw new IllegalStateException("Must invoke computeEncoding() first.");
        }
        final int length = word.length();
next:   for (int i=0; i<length;) {
            for (int j=Math.min(MAX_CHARACTERS, length-i); j>=1; j--) {
                final Integer code = encodingMap.get(word.substring(i, i+j));
                if (code != null) {
                    final int n = code;
                    if ((n & MASK_TWO_BYTES) == 0) {
                        buffer.put((byte) n);
                    } else {
                        buffer.putShort((short) n);
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
     *   <li>Number of character sequences, as an unsigned {@code short}.</li>
     *   <li>Length of each character sequence, stored on {@value #NUM_BITS_FOR_CHAR_LENGTH} bits.</li>
     *   <li>Length of the character sequence pool, as an {@code int}.</li>
     *   <li>All character sequences encoded in UTF-8 or UTF-16.</li>
     * </ul>
     *
     * @param out The channel where to write.
     * @param buffer A temporary buffer to use.
     */
    public void writeEncodingTable(final WritableByteChannel out, final ByteBuffer buffer) throws IOException {
        final String[] sequences = new String[encodingMap.size()];
        for (final Map.Entry<String,Integer> entry : encodingMap.entrySet()) {
            final int index = decodeTwoBytes(entry.getValue());
            if (sequences[index] != null) {
                throw new IllegalStateException("Key collision: " + index);
            }
            sequences[index] = entry.getKey();
        }
        /*
         * Write the number of character sequences.
         */
        assert sequences.length <= 0xFFFF;
        buffer.putShort((short) sequences.length);
        /*
         * Write the length of each character sequence.
         * Many lengths are packed in each 'long' value.
         */
        long packed = 0;
        for (int i=0; i<sequences.length;) {
            final int length = sequences[i].length();
            packed = (packed << NUM_BITS_FOR_CHAR_LENGTH) | (length - 1);
            assert ((length - 1) & ~(MAX_CHARACTERS - 1)) == 0 : length;
            if (++i % (Long.SIZE / NUM_BITS_FOR_CHAR_LENGTH) == 0) {
                buffer.putLong(packed);
                packed = 0;
                // Test after addition because we assume that the buffer was big enough
                // for the first long, and we want to ensure that there is enough room
                // for the long that may be added after this loop.
                if (buffer.remaining() < Long.SIZE / Byte.SIZE) {
                    writeFully(buffer, out);
                }
            }
        }
        // Final packed length, if any.
        if (sequences.length % (Long.SIZE / NUM_BITS_FOR_CHAR_LENGTH) != 0) {
            buffer.putLong(packed);
        }
        /*
         * Write all character sequences.
         */
        final StringBuilder string = new StringBuilder();
        for (final String sequence : sequences) {
            string.append(sequence);
        }
        final byte[] bytes = string.toString().getBytes(isAddingJapanese ? JAPAN_ENCODING : LATIN_ENCODING);
        buffer.putInt(bytes.length);
        writeFully(buffer, out);
        writeFully(ByteBuffer.wrap(bytes), out);
    }

    /**
     * Clears the frequency table. This method should be invoked before to reuse
     * this {@code FrequencyAnalyzer} object for a new language.
     */
    public void clear() {
        encodingMap.clear();
        encodingComputed = false;
    }
}
