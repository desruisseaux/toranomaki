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

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * Immutable set of {@link PartOfSpeech}. This set take advantage of the fact that we have few
 * part of speech (at most 8) for storing the set in a more efficient way than the standard
 * {@code EnumSet}.
 *
 * @author Martin Desruisseaux
 */
final class PartOfSpeechSet extends AbstractSet<PartOfSpeech> {
    /**
     * The code that identifies the part of speech.
     */
    private final long code;

    /**
     * Creates a new set for the given code.
     */
    PartOfSpeechSet(final long code) {
        this.code = code;
    }

    /**
     * Returns the number of elements in this set.
     */
    @Override
    public int size() {
        return (Long.SIZE - Long.numberOfLeadingZeros(code)) / Byte.SIZE;
    }

    /**
     * Returns an iterator over the elements in this set.
     */
    @Override
    public Iterator<PartOfSpeech> iterator() {
        return new Iter(code);
    }

    /**
     * The iterator.
     */
    private static final class Iter implements Iterator<PartOfSpeech> {
        /**
         * The part of speech values.
         */
        private static final PartOfSpeech[] VALUES = PartOfSpeech.values();

        /**
         * The packed index of the next elements to return.
         */
        private long code;

        /**
         * Returns a new iterator over all elements in this set.
         */
        Iter(final long code) {
            this.code = code;
        }

        /**
         * Returns {@code true} if there is more elements.
         */
        @Override
        public boolean hasNext() {
            return code != 0;
        }

        /**
         * Returns the next element.
         */
        @Override
        public PartOfSpeech next() {
            if (code == 0) {
                throw new NoSuchElementException();
            }
            final PartOfSpeech pos = VALUES[(int) (code & 0xFF) - 1];
            code >>>= Byte.SIZE;
            return pos;
        }

        /**
         * Unsupported operation.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
