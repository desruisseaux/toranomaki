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

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.stage.Modality;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.BorderPane;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;

import fr.toranomaki.grammar.AugmentedEntry;


/**
 * A dialog box proposing to add a new word to the list of words to learn.
 *
 * @author Martin Desruisseaux
 */
final class NewWordDialog implements EventHandler<ActionEvent> {
    /**
     * Identifiers used for the "Add" and "Cancel" buttons.
     */
    private static final String ADD="ADD", CANCEL="CANCEL";

    /**
     * The entry which may be added to the list of words to learn.
     */
    private final AugmentedEntry entry;

    /**
     * The Kanji elements declared in the entry to add.
     * There is often only one choice.
     */
    private final ChoiceBox<String> kebChoices;

    /**
     * The reading elements declared in the entry to add.
     * There is often only one choice.
     */
    private final ChoiceBox<String> rebChoices;

    /**
     * The window showing the dialog.
     */
    private Stage stage;

    /**
     * Where to add the new word.
     */
    private final Dictionary addTo;

    /**
     * Creates a new dialog for the given entry.
     */
    NewWordDialog(final AugmentedEntry entry, final Dictionary addTo) {
        this.entry = entry;
        this.addTo = addTo;
        kebChoices = new ChoiceBox<>();
        rebChoices = new ChoiceBox<>();
        kebChoices.setMaxWidth(200);
        rebChoices.setMaxWidth(200);
        boolean isKanji = true;
        do {
            final ChoiceBox<String> choices = isKanji ? kebChoices : rebChoices;
            final ObservableList<String> items = choices.getItems();
            final int count = entry.getCount(isKanji);
            for (int i=0; i<count; i++) {
                items.add(entry.getWord(isKanji, i));
                if (i == 0) {
                    choices.getSelectionModel().select(0);
                }
            }
        } while ((isKanji = !isKanji) == false);
    }

    /**
     * Shows the dialog box.
     */
    final void show() {
        final Label  explain = new Label("Confirm the addition of a new word to the training list:");
        final Label  kanjis  = new Label("Kanjis:");  kanjis .setLabelFor(kebChoices);
        final Label  reading = new Label("Reading:"); reading.setLabelFor(rebChoices);
        final Insets margin  = new Insets(3, 6, 3, 6);
        final GridPane grid = new GridPane();
        GridPane.setConstraints(kanjis,     0, 0);
        GridPane.setConstraints(reading,    0, 1);
        GridPane.setConstraints(kebChoices, 1, 0);
        GridPane.setConstraints(rebChoices, 1, 1);
        GridPane.setMargin(kanjis,     margin);
        GridPane.setMargin(reading,    margin);
        GridPane.setMargin(kebChoices, margin);
        GridPane.setMargin(rebChoices, margin);
        grid.getChildren().addAll(kanjis, kebChoices, reading, rebChoices);
        grid.setAlignment(Pos.CENTER);

        final Button confirm = new Button("Add");
        final Button cancel  = new Button("Cancel");
        confirm.setId(ADD);
        cancel .setId(CANCEL);
        confirm.setDefaultButton(true);
        cancel .setCancelButton(true);
        confirm.setMaxWidth(120);
        cancel .setMaxWidth(120);
        confirm.setOnAction(this);
        cancel .setOnAction(this);
        final TilePane buttons = new TilePane();
        buttons.setPrefColumns(2);
        buttons.setHgap(15);
        buttons.getChildren().addAll(confirm, cancel);
        buttons.setAlignment(Pos.CENTER);

        explain.setWrapText(true);
        BorderPane.setMargin(explain, new Insets(15, 15, 3, 15));
        BorderPane.setMargin(buttons, new Insets(15));
        final BorderPane pane = new BorderPane();
        pane.setPrefSize(300, 160);
        pane.setTop(explain);
        pane.setCenter(grid);
        pane.setBottom(buttons);

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(pane));
        stage.setResizable(false);
        stage.show();
    }

    /**
     * Invoked when a button has been pressed.
     */
    @Override
    public void handle(final ActionEvent event) {
        stage.close();
        switch (((Node) event.getSource()).getId()) {
            default: throw new AssertionError();
            case CANCEL: break;
            case ADD: {
                final String kanji   = kebChoices.getSelectionModel().getSelectedItem();
                final String reading = rebChoices.getSelectionModel().getSelectedItem();
                final WordToLearn word = new WordToLearn(kanji, reading);
                entry.setWordToLearn(kanji, reading);
                addTo.add(word);
                break;
            }
        }
    }
}
