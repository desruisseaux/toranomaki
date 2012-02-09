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
package fr.toranomaki;

import fr.toranomaki.edict.Entry;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableStringValue;


/**
 * An {@link Entry} value to be show in a {@link WordTable} column.
 * Current implementation presumes that entries are immutable.
 *
 * @author Martin Desruisseaux
 */
abstract class EntryValue implements ObservableStringValue {
    /**
     * The value to be returned by {@link #getValue()}.
     */
    protected String value;

    /**
     * Creates a new instance.
     */
    protected EntryValue() {
    }

    /**
     * Returns the {@linkplain #value}, which may be null.
     */
    @Override
    public String getValue() {
        return value;
    }

    /**
     * The Kanji value. We returns the first element, which should be the most common one.
     * Note that about 85% of entries declare only one Kanji element anyway.
     */
    static final class Kanji extends EntryValue {
        Kanji(final Entry entry) {
            value = entry.getWord(true, 0);
        }
    }

    /**
     * The reading value. We returns the first element, which should be the most common one.
     * Note that about 90% of entries declare only one reading element anyway.
     */
    static final class Reading extends EntryValue {
        Reading(final Entry entry) {
            value = entry.getWord(false, 0);
        }
    }

    /**
     * A comma-separated list of <cite>Part Of Speech</cite>.
     */
    static final class PartOfSpeech extends EntryValue {
        PartOfSpeech(final Entry entry) {
            final fr.toranomaki.edict.Sense sense = entry.getSense(null);
            if (sense != null) {
                value = sense.getPartOfSpeech();
            }
        }
    }

    /**
     * A comma-separated list of sense, in the preferred language if available.
     */
    static final class Sense extends EntryValue {
        Sense(final Entry entry) {
            final fr.toranomaki.edict.Sense sense = entry.getSense(null);
            if (sense != null) {
                value = sense.meaning;
            }
        }
    }

    /**
     * Delegates to {@link #getValue()}.
     */
    @Override
    public final String get() {
        return getValue();
    }

    /**
     * Do nothing, since entries are presumed immutable.
     */
    @Override
    public final void addListener(final ChangeListener<? super String> listener) {
    }

    /**
     * Do nothing, since entries are presumed immutable.
     */
    @Override
    public final void addListener(final InvalidationListener listener) {
    }

    /**
     * Do nothing, since no listener were registered.
     */
    @Override
    public final void removeListener(final ChangeListener<? super String> listener) {
    }

    /**
     * Do nothing, since no listener were registered.
     */
    @Override
    public final void removeListener(final InvalidationListener listener) {
    }
}
