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
import java.util.Arrays;
import java.util.Random;
import java.util.Collections;
import java.util.ResourceBundle;
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
final class TrainingPane implements EventHandler<ActionEvent> {
    /**
     * Number of words to ask in priority for the training. A value of 10 means that
     * the training will ask one of the 10 most difficult words most of the time.
     */
    private static final int NUM_PRIORITY_WORDS = 10;

    /**
     * The standard deviation of the Gaussian distribution of random numbers to pickup
     * for selecting a word. This determine the probability that the easiest words are
     * selected, compared to the probability that the most "difficult" words are selected.
     * A value of 1 means that "easiest" word has 37% of the probability of "difficult" words.
     * For values 1.5, 1.75 and 2, the probabilities are 11%, 5% and 2% respectively.
     */
    private static final double STANDARD_DEVIATION = 1.75;

    /**
     * Identifiers used for the "Easy", "Medium", "Hard", "Translate", "List words" and "Add word" buttons.
     */
    private static final String EASY="EASY", MEDIUM="MEDIUM", HARD="HARD",
            TRANSLATE="TRANSLATE", LIST_WORDS="LIST_WORDS", ADD_WORD="ADD_WORD";

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
    private final Button easy, medium, hard, listWords, addWord;

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
    TrainingPane(final Dictionary dictionary, final ExecutorService executor) throws IOException {
        final ResourceBundle resources = ResourceBundle.getBundle("fr/toranomaki/Resources");
        description  = new WordDescription();
        table        = new WordTable(description, dictionary, executor);
        query        = new Label();
        easy         = (Button)   createButton(EASY,       resources, false);
        medium       = (Button)   createButton(MEDIUM,     resources, false);
        hard         = (Button)   createButton(HARD,       resources, false);
        listWords    = (Button)   createButton(LIST_WORDS, resources, false);
        addWord      = (Button)   createButton(ADD_WORD,   resources, false);
        translate    = (CheckBox) createButton(TRANSLATE,  resources, true);
        random       = new Random();
        showNextWord();
    }

    /**
     * Creates a new button with a label fetched from the resource bundle using the given key.
     */
    private ButtonBase createButton(final String id, final ResourceBundle resources, final boolean checkbox) {
        final String label = resources.getString(id);
        final ButtonBase button;
        if (checkbox) {
            button = new CheckBox(label);
        } else {
            button = new Button(label);
            button.setMaxWidth(100);
        }
        button.setId(id);
        button.setOnAction(this);
        return button;
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Node createPane() {
        query.setAlignment(Pos.CENTER);
        query.setFont(Font.font(null, 24));

        final TilePane buttons = new TilePane();
        buttons.setHgap(9);
        buttons.setPrefRows(1);
        buttons.setPrefColumns(3);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(easy, medium, hard);

        final Insets margin = new Insets(12);
        VBox.setMargin(query,     margin);
        VBox.setMargin(translate, margin);
        VBox.setMargin(buttons,   margin);
        final VBox centerPane = new VBox();
        centerPane.setAlignment(Pos.CENTER);
        centerPane.getChildren().addAll(query, translate, buttons);

        final TilePane searchButtons = new TilePane();
        searchButtons.setHgap(3);
        searchButtons.setPrefRows(1);
        searchButtons.setPrefColumns(2);
        searchButtons.getChildren().addAll(listWords, addWord);
        searchButtons.setMaxWidth(TilePane.USE_PREF_SIZE);

        final Node desc = description.createPane();
        SplitPane.setResizableWithParent(desc, false);
        final SplitPane pane = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(desc, centerPane, table.createPane(searchButtons));
        pane.setDividerPositions(0.15, 0.6);

        pane.setOnKeyTyped(new EventHandler<KeyEvent>() {
            @Override public void handle(final KeyEvent key) {
                handleShurcut(key.getCharacter().toUpperCase());
            }
        });
        return pane;
    }

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
                final boolean isWordToLearn = entry.isWordToLearn();
                setButtonDisabled(!isWordToLearn, isWordToLearn);
                if (isWordToLearn && !translate.isSelected()) {
                    entry = null; // If the user didn't asked for a translation, hide it.
                }
            }
            super.setSelected(entry);
        }
    }

    /**
     * Enables or disables the buttons.
     *
     * @param knowledge {@code true} for disabling the "Easy" and "Hard" buttons.
     * @param addition  {@code true} for disabling the "New word" button.
     */
    final void setButtonDisabled(final boolean knowledge, final boolean addition) {
        easy     .setDisable(knowledge);
        medium   .setDisable(knowledge);
        hard     .setDisable(knowledge);
        translate.setDisable(knowledge);
        addWord  .setDisable(addition);
    }

    /**
     * Returns the list of words to learn.
     */
    final List<WordToLearn> getWordsToLearn() {
        return table.dictionary.wordsToLearn;
    }

    /**
     * Returns the current entry, or {@code null} if none.
     */
    private AugmentedEntry getEntry() {
        final List<WordToLearn> wordsToLearn = getWordsToLearn();
        if (wordsToLearn.isEmpty()) {
            return null;
        }
        return wordsToLearn.get(wordIndex).getEntry(table.dictionary);
    }

    /**
     * Show the next word to submit to the user.
     */
    private void showNextWord() {
        final List<WordToLearn> wordsToLearn = getWordsToLearn();
        AugmentedEntry entry = null;
        int last = wordIndex;
        while (!wordsToLearn.isEmpty()) {
            int size = wordsToLearn.size();
            if (size > NUM_PRIORITY_WORDS && random.nextBoolean()) {
                wordIndex = size - random.nextInt(NUM_PRIORITY_WORDS) - 1;
            } else {
                size -= NUM_PRIORITY_WORDS;
                wordIndex = ((int) (Math.abs(random.nextGaussian()) * size / STANDARD_DEVIATION)) % size;
            }
            if (wordIndex != last || size <= 1) {
                final WordToLearn word = wordsToLearn.get(wordIndex);
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
        final List<WordToLearn> wordsToLearn = getWordsToLearn();
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
                    int n = random.nextInt(Math.min(wordIndex, NUM_PRIORITY_WORDS)) + 1;
                    final WordToLearn word = wordsToLearn.get(wordIndex);
                    do {
                        wordsToLearn.set(wordIndex, wordsToLearn.get(--wordIndex));
                    } while (--n != 0);
                    wordsToLearn.set(wordIndex, word);
                }
                showNextWord();
                break;
            }
            /*
             * If the user identified the current word as of medium difficulty, move it up a
             * little bit in our list. This is a much less drastic move than for "hard" words.
             */
            case MEDIUM: {
                if (wordIndex != wordsToLearn.size() - 1) {
                    Collections.swap(wordsToLearn, wordIndex, ++wordIndex);
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
                    final WordToLearn word = wordsToLearn.get(wordIndex);
                    wordsToLearn.remove(wordIndex);
                    wordsToLearn.add(word);
                    wordIndex = last;
                }
                showNextWord();
                break;
            }
            /*
             * The user asked to show the list of words to learn.
             */
            case LIST_WORDS: {
                AugmentedEntry[] entries = new AugmentedEntry[wordsToLearn.size()];
                int n=0;
                for (int i=entries.length; --i>=0;) {
                    entries[n] = wordsToLearn.get(i).getEntry(table.dictionary);
                    if (entries[n] == null) {
                        Logging.possibleDataLost(TrainingPane.class, "handle",
                                "Can't find the entry for " + wordsToLearn.get(i));
                        wordsToLearn.remove(i);
                        continue;
                    }
                    n++;
                }
                if (n != entries.length) {
                    entries = Arrays.copyOf(entries, n);
                }
                table.setContent(entries, wordIndex);
                break;
            }
            /*
             * If the user selected a new word to learn, ask confirmation and add it to
             * out list.
             */
            case ADD_WORD: {
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
                case 2: button = medium;    break;
                case 3: button = hard;      break;
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
