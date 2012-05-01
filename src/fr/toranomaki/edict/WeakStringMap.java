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

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;


/**
 * A map of {@link String} instances hold by weak references. We use this map
 * because many {@link Entry} instances may use the same string.
 *
 * @author Martin Desruisseaux
 */
final class WeakStringMap {
    /**
     * Load factor. Control the moment where {@link #table} must be rebuild.
     */
    private static final float LOAD_FACTOR = 0.75f;

    /**
     * The mask to apply on key values in order to key hash code values.
     */
    private static final int MASK = 0x7FFFFFFF;

    /**
     * A weak reference to an element. This is an element in a linked list.
     * When the reference is disposed, it is removed from the enclosing set.
     */
    private static final class Element extends WeakReference<String> {
        /**
         * The key in the map for the string value.
         */
        final int key;

        /**
         * The next entry, or {@code null} if there is none.
         * This value is updated when the table is rehashed.
         */
        Element next;

        /**
         * Constructs a new weak reference.
         */
        Element(final int key, final String value, final ReferenceQueue<String> queue, final Element next) {
            super(value, queue);
            this.key  = key;
            this.next = next;
        }
    }

    /**
     * References which has been collected by the garbage collector.
     */
    private final ReferenceQueue<String> queue = new ReferenceQueue<>();

    /**
     * Table of weak references.
     */
    private Element[] table = new Element[997];

    /**
     * Number of non-null elements in {@link #table}.
     */
    private int count;

    /**
     * The next size value at which to resize. This value should
     * be <code>{@link #table}.length*{@link #LOAD_FACTOR}</code>.
     */
    private int threshold;

    /**
     * Constructs a {@code WeakStringMap}.
     */
    WeakStringMap() {
        threshold = Math.round(table.length * LOAD_FACTOR);
    }

    /**
     * Rehash {@link #table}.
     */
    private void rehash() {
        final Element[] oldTable = table;
        final Element[] table = new Element[oldTable.length * 2];
        this.table = table;
        threshold = Math.round(table.length * LOAD_FACTOR);
        for (int i=0; i<oldTable.length; i++) {
            for (Element next=oldTable[i]; next!=null;) {
                final Element e = next;
                next = next.next; // We keep 'next' right now because its value will change.
                assert (e.key & MASK) % oldTable.length == i;
                final int index = (e.key & MASK) % table.length;
                e.next = table[index];
                table[index] = e;
            }
        }
    }

    /**
     * Removes queued elements, if any.
     */
    private void removeQueued() {
        final Element[] table = this.table;
        Element toRemove;
        while ((toRemove = (Element) queue.poll()) != null) {
            final int index = (toRemove.key & MASK) % table.length;
            Element prev = null;
            Element e = table[index];
            while (e != null) {
                if (e == toRemove) {
                    if (prev != null) {
                        prev.next = e.next;
                    } else {
                        table[index] = e.next;
                    }
                    count--;
                    break;
                }
                prev = e;
                e = e.next;
            }
        }
    }

    /**
     * Returns the string value for the given key, or {@code null} if none.
     *
     * @param  value The key for which to get the string.
     * @return The string value for the given key, or {@code null} if none.
     */
    public synchronized String get(final int key) {
        removeQueued();
        final Element[] table = this.table;
        final int index = (key & MASK) % table.length;
        for (Element e=table[index]; e!=null; e=e.next) {
            if (e.key == key) {
                return e.get();
            }
        }
        return null;
    }

    /**
     * Associates the given string to the given key in this map.
     */
    public synchronized void put(final int key, final String value) {
        assert get(key) == null : value;
        removeQueued();
        if (count >= threshold) {
            rehash();
        }
        final Element[] table = this.table;
        final int index = (key & MASK) % table.length;
        table[index] = new Element(key, value, queue, table[index]);
        count++;
        assert get(key) == value;
    }
}
