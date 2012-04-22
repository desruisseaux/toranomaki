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

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.geometry.Orientation;

import fr.toranomaki.edict.DictionaryReader;


/**
 * The text editor, together with the table of word search results on the bottom.
 *
 * @author Martin Desruisseaux
 */
final class Editor implements AutoCloseable {
    /**
     * The panel showing a description of the selected word.
     */
    private final WordPanel description;

    /**
     * The editor area.
     */
    private final TextArea text;

    /**
     * The result of word search.
     */
    private final WordTable table;

    /**
     * Creates a new instance using the given dictionary for searching words.
     */
    Editor(final DictionaryReader dictionary) {
        description = new WordPanel();
        text        = new TextArea();
        table       = new WordTable(description, dictionary);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Control createPane() {
        final Node desc = description.createPane();
        SplitPane.setResizableWithParent(desc, false);
        final SplitPane pane = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(desc, text, table.createPane());
        pane.setDividerPositions(0.15, 0.6);
        return pane;
    }

    /**
     * Closes the resources used by this editor.
     */
    @Override
    public void close() {
        table.close();
    }
}
