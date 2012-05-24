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
import java.util.logging.Level;
import java.io.IOException;

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
     * Identifiers used for the "Known", "Unknown" and "New word" buttons.
     */
    private static final String KNOWN="KNOWN", UNKNOWN="UNKNOWN", NEW_WORD="NEW_WORD";

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
     * The list of words to learn. This list is sorted from easiest to more difficult words,
     * words, as indicated by the user by clicking on the "Known" or "Unknown" buttons.
     */
    private LearningWord[] wordsToLearn;

    /**
     * Number of valid entries in {@link #wordsToLearn}.
     */
    private int wordsToLearnCount;

    /**
     * Index of the word currently show.
     */
    private int wordIndex;

    /**
     * The random number generator to use for selecting the words to submit to the user.
     */
    private final Random random;

    /**
     * {@code true} if the list of words has been modified.
     */
    private boolean modified;

    /**
     * Creates a new instance using the given dictionary for searching the words to ask.
     *
     * @throws IOException If an error occurred while loading the list of words.
     */
    LearningPane(final DictionaryReader dictionary, final ExecutorService executor) throws IOException {
        query        = new WordPanel();
        table        = new WordTable(query, dictionary, executor);
        random       = new Random();
        wordsToLearn = LearningWord.load();
        wordsToLearnCount = wordsToLearn.length;
        showNextWord();
    }

    /**
     * Saves the list of words if it has been modified.
     */
    final void save() {
        if (modified) try {
            LearningWord.save(wordsToLearn, wordsToLearnCount);
        } catch (IOException e) {
            Logging.possibleDataLost(e);
        }
    }

    /**
     * Show the next word to submit to the user.
     */
    private void showNextWord() {
        AugmentedEntry entry = null;
        final LearningWord[] wordsToLearn = this.wordsToLearn; // Protect from changes.
        int last = wordIndex;
        while (wordsToLearnCount != 0) {
            wordIndex = random.nextInt(wordsToLearnCount); // TODO: give more weight to last entries.
            if (wordIndex != last || wordsToLearnCount == 1) {
                final LearningWord word = wordsToLearn[wordIndex];
                entry = word.getEntry(table.dictionary);
                if (entry != null) {
                    break; // Found the next word to show.
                }
                // Missing entry (should not happen).
                Logging.LOGGER.log(Level.WARNING, "No dictionary entry found for {0}.", word);
                System.arraycopy(wordsToLearn, wordIndex+1, wordsToLearn, wordIndex, --wordsToLearnCount - wordIndex);
                wordsToLearn[wordsToLearnCount] = null;
                if (last > wordIndex) {
                    last--;
                }
            }
        }
        query.setSelected(entry);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Node createPane() {
        final Button known   = new Button("Known");      known.setId(   KNOWN); known  .setOnAction(this);
        final Button unknown = new Button("Unknown");  unknown.setId( UNKNOWN); unknown.setOnAction(this);
        final Button newWord = new Button("New word"); newWord.setId(NEW_WORD); newWord.setOnAction(this);
        final TilePane buttons = new TilePane();
        buttons.setHgap(9);
        buttons.setPrefRows(1);
        buttons.setPrefColumns(2);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(known, unknown, newWord);

        final VBox group  = new VBox(18);
        group.getChildren().addAll(query.createPane(), buttons);

        final SplitPane pane = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(group, table.createPane());
        return pane;
    }

    /**
     * Invoked when a button has been pressed.
     */
    @Override
    public void handle(final ActionEvent event) {
        final LearningWord[] wordsToLearn = this.wordsToLearn; // Protect from changes.
        switch (((Node) event.getSource()).getId()) {
            default: {
                throw new AssertionError();
            }
            /*
             * If the user identified the current word as "easy", move it up in our list
             * of words. This will decrease the chances that the word is picked again by
             * the 'showNextWord()' method.
             */
            case KNOWN: {
                if (wordIndex != 0) {
                    final LearningWord word = wordsToLearn[  wordIndex];
                    wordsToLearn[wordIndex] = wordsToLearn[--wordIndex];
                    wordsToLearn[wordIndex] = word;
                    modified = true;
                }
                showNextWord();
                break;
            }
            /*
             * If the user identified the current word as "difficult", move it straight
             * to the end of our list of words. This will give to this word high chance
             * to be picked again by the 'showNextWord()' method.
             */
            case UNKNOWN: {
                final int length = wordsToLearnCount - (wordIndex+1);
                if (length != 0) {
                    final LearningWord word = wordsToLearn[wordIndex];
                    System.arraycopy(wordsToLearn, wordIndex+1, wordsToLearn, wordIndex, length);
                    wordsToLearn[wordIndex = wordsToLearnCount - 1] = word;
                    modified = true;
                }
                showNextWord();
                break;
            }
            /*
             * If the user selected a new word to learn, ask confirmation and add it to
             * out list.
             */
            case NEW_WORD: {
                final AugmentedEntry entry = query.getSelected();
                if (entry != null) {
                    new NewWordDialog(entry).show();
                }
                break;
            }
        }
    }
}
