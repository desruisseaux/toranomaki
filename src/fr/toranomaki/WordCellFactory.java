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
import javafx.scene.paint.Color;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import fr.toranomaki.edict.Entry;
import static fr.toranomaki.edict.Entry.WORD_INDEX;


/**
 * The factory for table cells. This factory sets the text font according the priority of the
 * cell value.
 *
 * @author Martin Desruisseaux
 */
final class WordCellFactory implements Callback<TableColumn<Entry,Entry>,TableCell<Entry,Entry>> {
    /**
     * {@code true} if this factory is for the <cite>Kanji element</cite> column, or
     * {@code false} if it is for the <cite>Reading element</cite> column.
     */
    private final boolean isKanji;

    /**
     * Creates a new cell factory.
     *
     * @param isKanji {@code true} if this factory is for the <cite>Kanji element</cite>
     *       column, or {@code false} if it is for the <cite>Reading element</cite> column.
     */
    WordCellFactory(final boolean isKanji) {
        this.isKanji = isKanji;
    }

    /**
     * Creates a cell renderer for the given column.
     */
    @Override
    public TableCell<Entry,Entry> call(final TableColumn<Entry,Entry> column) {
        return new Cell(isKanji);
    }

    /**
     * The cell renderer created by the enclosing factory class. This cell renderer will
     * render in bold character the Kanji or Reading elements having a priority under a
     * pre-defined threshold.
     *
     * @author Martin Desruisseaux
     */
    private static final class Cell extends TableCell<Entry,Entry> {
        /**
         * A copy of the {@link WordCellFactory#isKanji} value from the enclosing factory class.
         */
        private final boolean isKanji;

        /**
         * Creates a new cell renderer.
         */
        Cell(final boolean isKanji) {
            this.isKanji = isKanji;
        }

        /**
         * Invoked by the skin implementation to update the item associated with this cell.
         */
        @Override
        protected void updateItem(final Entry item, final boolean empty) {
            super.updateItem(item, empty);
            String text  = null;
            Color  color = Color.BLACK;
            if (item != null) {
                text = item.getWord(isKanji, WORD_INDEX);
                if (!isSelected()) {
                    if (item.isWordToLearn()) {
                        if (!isKanji) {
                            color = Color.DARKGREEN;
                        }
                    } else if (item.isPreferredForm(isKanji)) {
                        color = Color.DARKBLUE;
                    }
                }
            }
            setText(text);
            setTextFill(color);
        }
    }
}
