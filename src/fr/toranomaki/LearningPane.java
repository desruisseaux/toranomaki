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

import java.util.Random;
import java.util.concurrent.ExecutorService;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.layout.TilePane;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import fr.toranomaki.edict.DictionaryReader;
import fr.toranomaki.grammar.AugmentedEntry;


/**
 * Provides the functionalities for vocabulary training.
 *
 * @author Martin Desruisseaux
 */
final class LearningPane implements EventHandler<ActionEvent> {
    /**
     * The list of words to use for the training.
     */
    private final WordTable table;

    /**
     * A selected word to submit to the user.
     * The user will need to tell whatever he know that word or not.
     */
    private final WordPanel query;

    /**
     * The list of words to learn.
     */
    private LearningWord[] wordsToLearn;

    /**
     * The random number generator to use for selecting the words to submit to the user.
     */
    private final Random random;

    /**
     * Creates a new instance using the given dictionary for searching the words to ask.
     */
    LearningPane(final DictionaryReader dictionary, final ExecutorService executor) {
        query = new WordPanel();
        table = new WordTable(query, dictionary, executor);
        random = new Random();
        /*
         * FOR TESTING PURPOSE ONLY
         */
        wordsToLearn = new LearningWord[] {
            new LearningWord("明日", "あした"),
            new LearningWord("知る", "しる")
        };
    }

    /**
     * Show the next word to submit to the user.
     */
    private void showNextWord() {
        int index = random.nextInt(wordsToLearn.length); // TODO: give more weight to first entries.
        final AugmentedEntry entry = wordsToLearn[index].getEntry(table.dictionary);
        query.setSelected(entry);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Node createPane() {
        final Button known   = new Button("Known");
        final Button unknown = new Button("Unknown");
        final TilePane buttons = new TilePane();
        buttons.setHgap(9);
        buttons.setPrefRows(1);
        buttons.setPrefColumns(2);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(known, unknown);

        final VBox group  = new VBox(18);
        group.getChildren().addAll(query.createPane(), buttons);

        final SplitPane pane = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(group, table.createPane());

        known.setOnAction(this);
        unknown.setOnAction(this);
        return pane;
    }

    /**
     * Invoked when a button has been pressed.
     */
    @Override
    public void handle(final ActionEvent event) {
        // TODO: more work to do here.
        showNextWord();
    }
}
