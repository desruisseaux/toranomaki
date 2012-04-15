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

import java.util.Arrays;
import fr.toranomaki.edict.Entry;


/**
 * The list of entries associated to a word.
 *
 * @author Martin Desruisseaux
 */
final class EntryList {
    /**
     * The entries included in this list.
     */
    private Entry[] entries;

    /**
     * The list from which this list is a sublist, or {@code null}.
     */
    final EntryList isSublistOf;

    /**
     * If this list is a sublist of another list, the offset index in that other list.
     */
    final int sublistIndex;

    /**
     * The position of this list in the stream, in units of
     * {@value WordToEntries#NUM_BYTES_FOR_ENTRY_POSITION}.
     */
    int position;

    /**
     * Creates a new list with a single entry
     */
    EntryList(final Entry entry) {
        entries = new Entry[] {entry};
        isSublistOf = null;
        sublistIndex = 0;
    }

    /**
     * Creates a new list as a sublist of the given list.
     */
    private EntryList(final EntryList parent, final int sublistStart, final int sublistEnd) {
        entries = Arrays.copyOfRange(parent.entries, sublistStart, sublistEnd);
        isSublistOf  = parent;
        sublistIndex = sublistStart;
    }

    /**
     * Returns the number of elements in this list.
     */
    int size() {
        return entries.length;
    }

    /**
     * Returns the entries. This method returns a direct reference to the internal
     * array without cloning, so do not modify the elements in that array.
     */
    Entry[] entries() {
        return entries;
    }

    /**
     * Adds a new element in this list. Note that this method is not
     * expected to be invoked very often.
     */
    void add(final Entry entry) {
        final int length = entries.length;
        entries = Arrays.copyOf(entries, length+1);
        entries[length] = entry;
    }

    /**
     * Returns a sublist of this list.
     */
    EntryList sublist(final int start, final int end) {
        if (start == 0 && end == entries.length) {
            return this;
        }
        return new EntryList(this, start, end);
    }

    /**
     * Returns a hash code for the array of entries.
     */
    @Override
    public int hashCode() {
        int code = 0;
        for (final Entry entry : entries) {
            code = code*31 + System.identityHashCode(entry);
        }
        return code;
    }

    /**
     * Compares the elements of the entries arrays.
     * This method performs identity comparison on intend.
     */
    @Override
    public boolean equals(final Object other) {
        if (other instanceof EntryList) {
            final Entry[] ta = entries;
            final Entry[] oa = ((EntryList) other).entries;
            if (ta.length == oa.length) {
                for (int i=0; i<ta.length; i++) {
                    if (ta[i] != oa[i]) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
}
