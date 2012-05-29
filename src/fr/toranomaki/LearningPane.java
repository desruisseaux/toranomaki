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

import java.util.List;
import java.util.Random;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.io.IOException;

import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.TilePane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ButtonBase;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import fr.toranomaki.grammar.AugmentedEntry;


/**
 * Provides the functionalities for vocabulary training.
 *
 * @author Martin Desruisseaux
 */
final class LearningPane implements EventHandler<ActionEvent> {
    /**
     * The panel which describe the word which has been asked to the user.
     */
    private final class WordDescription extends WordPanel {
        /**
         * Enables the "add word" button only if the selected word is a new word.
         */
        @Override
        void setSelected(AugmentedEntry entry) {
            if (entry == null) {
                // If the user did a search in the table of words, then cleared the search,
                // go back to the state where he choose if the word was "easy" or "hard".
                setButtonDisabled(getWordsToLearn().isEmpty(), true);
            } else {
                final boolean isLearningWord = entry.isLearningWord();
                setButtonDisabled(!isLearningWord, isLearningWord);
                if (isLearningWord && !translate.isSelected()) {
                    entry = null; // If the user didn't asked for a translation, hide it.
                }
            }
            super.setSelected(entry);
        }
    }

    /**
     * Identifiers used for the "Easy", "Hard", "Translate" and "New word" buttons.
     */
    private static final String EASY="EASY", HARD="HARD", TRANSLATE="TRANSLATE", NEW_WORD="NEW_WORD";

    /**
     * The list of words to use for the training.
     */
    private final WordTable table;

    /**
     * The description of a word selected in the {@linkplain #table},
     * or the word which is being asked to the user.
     */
    private final WordPanel description;

    /**
     * A selected word to submit to the user.
     * The user will need to tell whatever he know that word or not.
     */
    private final Label query;

    /**
     * The buttons displayed below the query label.
     */
    private final Button easy, hard, newWord;

    /**
     * The checkbox for asking to show the translation.
     */
    private final CheckBox translate;

    /**
     * Index of the word currently show.
     */
    private int wordIndex;

    /**
     * The random number generator to use for selecting the words to submit to the user.
     */
    private final Random random;

    /**
     * Creates a new instance using the given dictionary for searching the words to ask.
     *
     * @throws IOException If an error occurred while loading the list of words.
     */
    LearningPane(final Dictionary dictionary, final ExecutorService executor) throws IOException {
        description  = new WordDescription();
        table        = new WordTable(description, dictionary, executor);
        query        = new Label();
        easy         = new Button("Easy");        easy     .setId(EASY);
        hard         = new Button("Hard");        hard     .setId(HARD);
        newWord      = new Button("New word");    newWord  .setId(NEW_WORD);
        translate    = new CheckBox("Translate"); translate.setId(TRANSLATE);
        random       = new Random();
        showNextWord();
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Node createPane() {
        query    .setAlignment(Pos.CENTER);
        query    .setFont(Font.font(null, 24));
        easy     .setOnAction(this);
        hard     .setOnAction(this);
        newWord  .setOnAction(this);
        translate.setOnAction(this);
        easy     .setMaxWidth(100);
        hard     .setMaxWidth(100);
        newWord  .setMaxWidth(100);
        final TilePane buttons = new TilePane();
        buttons.setHgap(9);
        buttons.setPrefRows(1);
        buttons.setPrefColumns(2);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(easy, hard, newWord);

        final Insets margin = new Insets(12);
        VBox.setMargin(query,     margin);
        VBox.setMargin(translate, margin);
        VBox.setMargin(buttons,   margin);
        final VBox centerPane = new VBox();
        centerPane.setAlignment(Pos.CENTER);
        centerPane.getChildren().addAll(query, translate, buttons);

        final Node desc = description.createPane();
        SplitPane.setResizableWithParent(desc, false);
        final SplitPane pane = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(desc, centerPane, table.createPane());
        pane.setDividerPositions(0.15, 0.6);

        pane.setOnKeyTyped(new EventHandler<KeyEvent>() {
            @Override public void handle(final KeyEvent key) {
                handleShurcut(key.getCharacter().toUpperCase());
            }
        });
        return pane;
    }

    /**
     * Enable or disable the buttons.
     *
     * @param knowledge {@code true} for disabling the "Easy" and "Hard" buttons.
     * @param addition  {@code true} for disabling the "New word" button.
     */
    final void setButtonDisabled(final boolean knowledge, final boolean addition) {
        easy   .setDisable(knowledge);
        hard   .setDisable(knowledge);
        newWord.setDisable(addition);
    }

    /**
     * Returns the list of words to learn.
     */
    final List<LearningWord> getWordsToLearn() {
        return table.dictionary.wordsToLearn;
    }

    /**
     * Returns the current entry, or {@code null} if none.
     */
    private AugmentedEntry getEntry() {
        final List<LearningWord> wordsToLearn = getWordsToLearn();
        if (wordsToLearn.isEmpty()) {
            return null;
        }
        return wordsToLearn.get(wordIndex).getEntry(table.dictionary);
    }

    /**
     * Show the next word to submit to the user.
     */
    private void showNextWord() {
        final List<LearningWord> wordsToLearn = getWordsToLearn();
        AugmentedEntry entry = null;
        int last = wordIndex;
        while (!wordsToLearn.isEmpty()) {
            wordIndex = random.nextInt(wordsToLearn.size()); // TODO: give more weight to last entries.
            if (wordIndex != last || wordsToLearn.size() == 1) {
                final LearningWord word = wordsToLearn.get(wordIndex);
                entry = word.getEntry(table.dictionary);
                if (entry != null) {
                    // Found the next word to show.
                    query.setText(word.getQueryText());
                    translate.setSelected(false);
                    break;
                }
                // Missing entry (should not happen).
                Logging.LOGGER.log(Level.WARNING, "No dictionary entry found for {0}.", word);
                wordsToLearn.remove(wordIndex);
                if (last > wordIndex) {
                    last--;
                }
            }
        }
        description.setSelected(entry);
    }

    /**
     * Invoked when a button has been pressed.
     */
    @Override
    public void handle(final ActionEvent event) {
        handle(((Node) event.getSource()).getId());
    }

    /**
     * Implementation of {@link #handle(ActionEvent)}.
     */
    private void handle(final String id) {
        final List<LearningWord> wordsToLearn = getWordsToLearn();
        switch (id) {
            default: {
                throw new AssertionError();
            }
            /*
             * If the user identified the current word as "easy", move it up in our list
             * of words. This will decrease the chances that the word is picked again by
             * the 'showNextWord()' method.
             */
            case EASY: {
                if (wordIndex != 0) {
                    Collections.swap(wordsToLearn, wordIndex, --wordIndex);
                }
                showNextWord();
                break;
            }
            /*
             * If the user identified the current word as "difficult", move it straight
             * to the end of our list of words. This will give to this word high chance
             * to be picked again by the 'showNextWord()' method.
             */
            case HARD: {
                final int last = wordsToLearn.size() - 1;
                if (wordIndex != last) {
                    final LearningWord word = wordsToLearn.get(wordIndex);
                    wordsToLearn.remove(wordIndex);
                    wordsToLearn.add(word);
                    wordIndex = last;
                }
                showNextWord();
                break;
            }
            /*
             * If the user selected a new word to learn, ask confirmation and add it to
             * out list.
             */
            case NEW_WORD: {
                final AugmentedEntry entry = description.getSelected();
                if (entry != null) {
                    new NewWordDialog(entry, table.dictionary).show();
                }
                break;
            }
            /*
             * The user asked to show or hide the translation.
             */
            case TRANSLATE: {
                description.setSelected(getEntry());
            }
        }
    }

    /**
     * Handles a key which has been pressed.
     */
    final void handleShurcut(final String key) {
        for (int i=0; i<4; i++) {
            final ButtonBase button;
            switch (i) {
                case 0: button = translate; break;
                case 1: button = easy;      break;
                case 2: button = hard;      break;
                case 3: button = newWord;   break;
                default: throw new AssertionError(i);
            }
            if (button.getText().startsWith(key)) {
                if (button instanceof CheckBox) {
                    final CheckBox c = ((CheckBox) button);
                    c.setSelected(!c.isSelected());
                }
                handle(button.getId());
            }
        }
    }
}
