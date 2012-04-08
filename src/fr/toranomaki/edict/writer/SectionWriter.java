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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;


/**
 * Base class for writer of one section of the dictionary file.
 *
 * @author Martin Desruisseaux
 */
abstract class SectionWriter {
    /**
     * {@code true} for adding Japanese words, or {@code false} for adding senses.
     */
    boolean isAddingJapanese;

    /**
     * Creates a new writer instance.
     */
    SectionWriter() {
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
