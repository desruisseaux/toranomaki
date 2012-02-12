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

import javafx.util.Callback;
import javafx.scene.control.TableColumn;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;

import fr.toranomaki.edict.Entry;


/**
 * An {@link Entry} value to be show in a {@link WordTable} column.
 * Current implementation presumes that entries are immutable.
 * <p>
 * The default implementation just returns the {@code Entry} unchanged. Instances of the default
 * implementation are created by {@link DefaultFactory}.
 * <p>
 * Various {@code WordElementValue} factories are provided at the end of this method.
 *
 * @author Martin Desruisseaux
 */
class WordElementValue<T> implements ObservableObjectValue<T> {
    /**
     * An {@link WordElementValue} specialization which build a comma-separated list of
     * <cite>Part Of Speech</cite> or senses, in the preferred language if available.
     */
    static final class Sense extends WordElementValue<String> implements ObservableStringValue {
        Sense(final Entry entry, final boolean isPartOfSpeech) {
            final fr.toranomaki.edict.Sense sense = entry.getSense(null);
            if (sense != null) {
                value = isPartOfSpeech ? sense.getGrammaticalClass() : sense.meaning;
            }
        }
    }

    /**
     * A factory for {@link Sense} values. Those implementations compute the {@link String}
     * value right at construction time. Use this implementation when the full {@link Entry}
     * instance is not needed at rendering time.
     */
    static final class SenseFactory implements Callback<TableColumn.CellDataFeatures<WordElement,String>, ObservableValue<String>> {
        /** {@code true} for the <cite>part of speech</cite> column, or {@code false} for the <cite>meaning</cite> column. */
        private final boolean isPartOfSpeech;

        SenseFactory(final boolean isPartOfSpeech) {
            this.isPartOfSpeech = isPartOfSpeech;
        }

        @Override public ObservableValue<String> call(final TableColumn.CellDataFeatures<WordElement,String> cell) {
            return new WordElementValue.Sense(cell.getValue().entry, isPartOfSpeech);
        }
    }

    /**
     * A factory which returns directly the {@link Entry} cell value without any processing.
     * The {@link CellFactory} class will be responsible for invoking the appropriate method
     * on the {@link Entry} instance in order to get the desired value. Use this factory only
     * when the while {@link Entry} instance is needed at cell rendering time, typically because
     * we need other information (like the priority) to decide how to render the value.
     */
    static final class DefaultFactory implements Callback<TableColumn.CellDataFeatures<WordElement,WordElement>, ObservableValue<WordElement>> {
        @Override public ObservableValue<WordElement> call(final TableColumn.CellDataFeatures<WordElement,WordElement> cell) {
            final WordElementValue<WordElement> value = new WordElementValue<>();
            value.value = cell.getValue();
            return value;
        }
    }

    /**
     * The value to be returned by {@link #get()}.
     */
    protected T value;

    /**
     * Creates a new instance.
     */
    WordElementValue() {
    }

    /**
     * Returns the {@linkplain #value}, which may be null.
     */
    @Override
    public final T get() {
        return value;
    }

    /**
     * Returns the {@linkplain #value}, which may be null.
     */
    @Override
    public final T getValue() {
        return value;
    }

    /**
     * Do nothing, since entries are presumed immutable.
     */
    @Override
    public final void addListener(final ChangeListener<? super T> listener) {
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
    public final void removeListener(final ChangeListener<? super T> listener) {
    }

    /**
     * Do nothing, since no listener were registered.
     */
    @Override
    public final void removeListener(final InvalidationListener listener) {
    }
}
