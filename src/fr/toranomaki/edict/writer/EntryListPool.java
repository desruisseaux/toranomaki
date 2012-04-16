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
import java.util.Arrays;
import java.util.Collection;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.DictionaryFile;
import static fr.toranomaki.edict.writer.WordEncoder.writeFully;


/**
 * A pool of {@link EntryList} instances.
 *
 * @author Martin Desruisseaux
 */
final class EntryListPool extends DictionaryFile {
    /**
     * The entry lists.
     */
    private final EntryList[] lists;

    /**
     * The pool size, in bytes.
     */
    final int size;

    /**
     * Creates a new pool of entry lists.
     */
    EntryListPool(final Collection<EntryList> sublists) {
        lists = sublists.toArray(new EntryList[sublists.size()]);
        Arrays.sort(lists);
        int position = 0;
        for (final EntryList list : lists) {
            list.position = position;
            position += list.size();
        }
        size = position * NUM_BYTES_FOR_ENTRY_POSITION;
    }

    /**
     * Writes the list of entries (actually references to entries).
     * This list is shared by all {@link WordToEntries} instances.
     *
     * @param entryPositions A map of entries to their location in the stream.
     * @param buffer         A temporary buffer to use for writing.
     * @param out            Where to flush the buffer.
     */
    void write(final Map<Entry,Integer> entryPositions,
            final ByteBuffer buffer, final WritableByteChannel out) throws IOException
    {
        for (final EntryList list : lists) {
            for (final Entry entry : list.entries()) {
                if (buffer.remaining() < NUM_BYTES_FOR_ENTRY_POSITION) {
                    writeFully(buffer, out);
                }
                int reference = entryPositions.get(entry);
                for (int i=NUM_BYTES_FOR_ENTRY_POSITION; --i>=0;) {
                    buffer.put((byte) reference);
                    reference >>= Byte.SIZE;
                }
                if (reference != 0) {
                    throw new IllegalArgumentException("Reference to " + entry + " is too large.");
                }
            }
        }
    }
}
