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
     * Creates a new mapping for the given collection of entries.
     * This constructor create the mapping of all words to their entries, but does not
     * yet compute the position of those entries in the binary stream. To compute their
     * position, invoke {@link #computePositions(WordToEntries[])} after creation.
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
     * Compares the given elements in such a way that shortest lists are sorted first.
     * Shortest lists are first because we want longest lists to overwrite shortest ones,
     * when iterating over the sorted {@code EntryList} array in ascending index order.
     */
    @Override
    public int compare(final EntryList o1, final EntryList o2) {
        return o1.size() - o2.size();
    }

    /**
     * Invoked after every {@code WordToEntries} instances has been created, in order to
     * compute the position of each entry in the binary stream.
     *
     * @param  references All {@code WordToEntries} for which to compute the position of entries.
     * @return The common list of entries to save.
     */
    static EntryList[] computePositions(final WordToEntries... references) {
        /*
         * Create the collection of all sublists of entries lists. If there is many lists
         * can produce the same sublist, keep the sublist created by the longest list.
         */
        final Map<EntryList, EntryList> subLists = new HashMap<>(256 * 1024);
        for (final WordToEntries ref : references) {
            final Collection<EntryList> entries = ref.entriesForWord.values();
            final EntryList[] sortedLists = entries.toArray(new EntryList[entries.size()]);
            Arrays.sort(sortedLists, ref);
            for (final EntryList list : sortedLists) {
                final int length = list.size();
                for (int start=0; start<length; start++) {
                    for (int end=start+1; end<=length; end++) {
                        final EntryList sublist = list.sublist(start, end);
                        final EntryList old = subLists.put(sublist, sublist);
                        if (old != null && old.parentSize() > sublist.parentSize()) {
                            subLists.put(old, old);
                        }
                    }
                }
            }
        }
        /*
         * Now that we found all possible sublists, replace the references of all WordEntries.
         * Then get the set of unique instance of list, discarting all sublists.
         */
        for (final WordToEntries ref : references) {
            for (final Map.Entry<String,EntryList> entry : ref.entriesForWord.entrySet()) {
                entry.setValue(subLists.get(entry.getValue()));
            }
        }
        subLists.clear();
        for (final WordToEntries ref : references) {
            for (final EntryList list : ref.entriesForWord.values()) {
                if (list.isSublistOf == null) {
                    subLists.put(list, null);
                }
            }
        }
        /*
         * Finally, computes the position of each list. We will sort first the
         * references to the entries that are declared first in the XML file.
         */
        final EntryList[] entries = subLists.keySet().toArray(new EntryList[subLists.size()]);
        Arrays.sort(entries);
        int position = 0;
        for (final EntryList list : entries) {
            list.position = position;
            position += list.size();
        }
        return entries;
    }

    /**
     * Returns the length of the pool of entry lists, in bytes.
     */
    static int entryListPoolSize(final EntryList[] lists) {
        final EntryList last = lists[lists.length - 1];
        return (last.position + last.size()) * NUM_BYTES_FOR_ENTRY_POSITION;
    }

    /**
     * Writes the references to the list of entries. For each word, the reference to be
     * written is the position and length of the entry list, packed on an {@code int}.
     *
     * @param words  The words, sorted in the order used by the index.
     * @param buffer A temporary buffer to use for writing.
     * @param out    Where to flush the buffer.
     */
    void writeReferences(final String[] words, final ByteBuffer buffer, final WritableByteChannel out) throws IOException {
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
        writeFully(buffer, out);
    }

    /**
     * Writes the list of entries (actually references to entries).
     * This list is shared by all {@link WordToEntries} instances.
     *
     * @param lists          The lists calculated by {@link #computePositions(WordToEntries[])}.
     * @param entryPositions A map of entries to their location in the stream.
     * @param buffer         A temporary buffer to use for writing.
     * @param out            Where to flush the buffer.
     */
    static void writeLists(final EntryList[] lists, final Map<Entry,Integer> entryPositions,
            final ByteBuffer buffer, final WritableByteChannel out) throws IOException {
        /*
         * Writes the references to the entries.
         */
        for (final EntryList list : lists) {
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
