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

import java.io.IOException;
import java.io.EOFException;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;


/**
 * Base class of every classes for reading or writing the dictionary file in its binary format.
 * The binary format is specific to this project and is documented in the
 * {@link fr.toranomaki.edict.writer} package.
 *
 * @author Martin Desruisseaux
 */
public abstract class DictionaryFile {
    /**
     * Arbitrary magic number. The value on the right side of {@code +} is the version number,
     * to be incremented every time we apply an incompatible change in the file format.
     */
    protected static final int MAGIC_NUMBER = 810241902 + 2;

    /**
     * The byte order used in the dictionary files.
     * This is fixed to the byte order used by Intel processors for now.
     */
    protected static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    /**
     * The encoding used for Latin or Japanese characters. Those encodings are used only
     * in the header of the binary file. After the header, we will use our own encoding
     * which will replace the most frequent character sequence by a code.
     */
    protected static final String LATIN_ENCODING = "UTF-8",
                                  JAPAN_ENCODING = "UTF-16";

    /**
     * The mask indicating that a character is encoded on two bytes rather than one.
     * We store the 128 most common characters on 1 byte, and the remaining on two
     * bytes.
     */
    protected static final int MASK_CODE_ON_TWO_BYTES = 0x80;

    /**
     * Number of bits to use for storing the word length in a {@code int} reference value.
     * The remaining number of bits will be used for storing the word start position.
     */
    protected static final int NUM_BITS_FOR_WORD_LENGTH = 9;

    /**
     * Size in bytes of one index value in the index array, which is the size of the {@code int} type.
     *
     * <p>Note: if this value is changed, we recommend to perform a search for {@code Integer.SIZE}
     * on the code base, especially in {@code WordIndexReader} and {@code WordIndexWriter}.</p>
     */
    protected static final int ELEMENT_SIZE = Integer.SIZE / Byte.SIZE;

    /**
     * For subclass constructors only.
     */
    protected DictionaryFile() {
    }

    /**
     * Reads bytes from the given channel until the given buffer is full, then flips the buffer.
     */
    static void readFully(final ReadableByteChannel in, final ByteBuffer buffer) throws IOException {
        do if (in.read(buffer) < 0) {
            throw new EOFException();
        } while (buffer.hasRemaining());
        buffer.flip();
    }
}
