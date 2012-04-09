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


/**
 * The encoded representation of a word, for internal usage by {@link WordIndexWriter} only.
 * <p>
 * The natural ordering is determined by the bytes sequence, by iterating over the bytes
 * <strong>in reverse order</strong> (from the last bytes to the first one). We use reverse
 * order because we will store in the {@link WordIndexWriter#wordFragments} map the same bytes
 * with more and more tail bytes omitted. So the map will contains many words with the same
 * prefix, which is conform to the practice found in many language where many words have the
 * same prefix but different suffixes.
 *
 * @author Martin Desruisseaux
 */
final class EncodedWord implements Comparable<EncodedWord> {
    /**
     * The original word, or {@code null} if this object is for a substring.
     */
    String word;

    /**
     * The encoded word.
     */
    final byte[] bytes;

    /**
     * Number of valid bytes in the {@link #bytes} array. Extra bytes are ignored.
     */
    final short length;

    /**
     * Non-null if an other instance contains the same bytes sequence.
     */
    EncodedWord isSubstringOf;

    /**
     * Position in the file, for {@link WordIndexWriter#write(Path)} internal usage.
     */
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
