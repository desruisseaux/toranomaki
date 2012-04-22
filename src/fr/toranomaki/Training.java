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

import javafx.scene.layout.Pane;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.Label;

import fr.toranomaki.edict.DictionaryReader;


/**
 * Provides the functionalities for vocabulary training.
 *
 * @author Martin Desruisseaux
 */
final class Training {
    /**
     * The dictionary to use for searching words.
     */
    private final DictionaryReader dictionary;

    /**
     * Creates a new instance using the given dictionary for searching the words to ask.
     */
    Training(final DictionaryReader dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Pane createPane() {
        final BorderPane pane = new BorderPane();
        pane.setCenter(new Label("Not yet implemented"));
        return pane;
    }
}
