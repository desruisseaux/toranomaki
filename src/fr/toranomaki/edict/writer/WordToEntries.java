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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.DictionaryFile;
import static fr.toranomaki.edict.writer.WordEncoder.writeFully;


/**
 * The mapping from words to entries having this word.
 *
 * @author Martin Desruisseaux
 */
final class WordToEntries extends DictionaryFile implements Comparator<EntryList> {
    /**
     * The entries associated to each word.
     */
    private final Map<String, EntryList> entriesForWord;

    /**
     * The length of list of entries in the stream, in units of
     * {@value #NUM_BYTES_FOR_ENTRY_POSITION}.
     */
    final int entriesListLength;

    /**
     * Creates a new mapping for the given collection of entries.
     *
     * @param entries  The entries for which to create en encoder.
     * @param japanese {@code true} for adding Japanese words, or {@code false} for adding senses.
     */
    WordToEntries(final Collection<Entry> entries, final boolean japanese) {
        entriesForWord = new LinkedHashMap<>(2 * entries.size());
        for (final Entry entry : entries) {
            if (japanese) {
                boolean isKanji = false;
                do {
                    final int count = entry.getCount(isKanji);
                    for (int i=0; i<count; i++) {
                        add(entry.getWord(isKanji, i), entry);
                    }
                } while ((isKanji = !isKanji) == true);
            } else {
                for (final Sense sense : entry.getSenses()) {
                    add(sense.meaning, entry);
                }
            }
        }
        /*
         * If a value from the 'entriesForWord' map is a sub-array of an other value,
         * store this information.
         */
        final Map<EntryList, EntryList> subLists = new HashMap<>(2 * entriesForWord.size());
        for (final EntryList list : getSortedEntryLists()) {
            final int length = list.size();
            for (int start=0; start<length; start++) {
                for (int end=start+1; end<=length; end++) {
                    final EntryList sublist = list.sublist(start, end);
                    subLists.put(sublist, sublist);
                }
            }
        }
        for (final Map.Entry<String,EntryList> entry : entriesForWord.entrySet()) {
            entry.setValue(subLists.get(entry.getValue()));
        }
        /*
         * Finally, computes the position of each list, excluding sublists.
         */
        int position = 0;
        for (final EntryList list : entriesForWord.values()) {
            if (list.isSublistOf == null) {
                list.position = position;
                position += list.size();
            }
        }
        entriesListLength = position;
    }

    /**
     * Adds the given entry to the set of entries associated to the given word.
     */
    private void add(final String word, final Entry entry) {
        EntryList entries = entriesForWord.get(word);
        if (entries == null) {
            entries = new EntryList(entry);
            entriesForWord.put(word, entries);
        } else {
            entries.add(entry);
        }
    }

    /**
     * Returns all {@link EntryList} elements, sorted in ascending list size.
     */
    private EntryList[] getSortedEntryLists() {
        final EntryList[] sortedLists = entriesForWord.values().toArray(new EntryList[entriesForWord.size()]);
        Arrays.sort(sortedLists, this);
        return sortedLists;
    }

    /**
     * Compares the given elements in such a way that shortest lists are sorted first.
     * Shortest lists are first because we want longest lists to overwrite shortest ones,
     * when iterating over the sorted {@code EntryList} array in ascending index order.
     */
    @Override
    public int compare(final EntryList o1, final EntryList o2) {
        return o1.size() - o2.size();
    }

    /**
     * Writes the references from words to entries.
     *
     * @param words          The words, sorted in the order used by the index.
     * @param entryPositions A map of entries to their location in the stream.
     * @param buffer         A temporary buffer to use for writing.
     * @param out            Where to flush the buffer.
     */
    void write(final String[] words, final Map<Entry,Integer> entryPositions,
            final ByteBuffer buffer, final WritableByteChannel out) throws IOException
    {
        /*
         * Write the position and length of each sequence of references to entries.
         */
        for (final String word : words) {
            EntryList list = entriesForWord.get(word);
            final int length = list.size();
            int position = list.sublistIndex;
            while (list.isSublistOf != null) {
                list = list.isSublistOf;
                position += list.sublistIndex;
            }
            position += list.position;
            /*
             * Encode the position on 3 bytes, followed by the length on 1 byte.
             */
            assert (position & 0xFF000000) == 0 && (length & 0xFFFFFF00) == 0;
            position <<= Byte.SIZE;
            position |= length;
            if (buffer.remaining() < (Integer.SIZE / Byte.SIZE)) {
                writeFully(buffer, out);
            }
            buffer.putInt(position);
        }
        /*
         * Writes the references to the entries.
         */
        for (final EntryList list : entriesForWord.values()) {
            if (list.isSublistOf == null) {
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
        writeFully(buffer, out);
    }
}
