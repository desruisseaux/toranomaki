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

import java.sql.SQLException;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.geometry.Orientation;

import fr.toranomaki.edict.JMdict;


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
    Editor(final JMdict dictionary) {
        description = new WordPanel(dictionary);
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
     * Closes the resources used by this editor. Note that this method closes also
     * the connection to the SQL database, which is shared by the {@link Training} pane.
     */
    @Override
    public void close() throws SQLException {
        table.close();
    }
}
