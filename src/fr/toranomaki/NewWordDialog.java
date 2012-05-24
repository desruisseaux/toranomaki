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
import javafx.stage.Modality;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.BorderPane;
import javafx.collections.ObservableList;

import fr.toranomaki.grammar.AugmentedEntry;
import javafx.geometry.Pos;


/**
 * A dialog box proposing to add a new word to the list of words to learn.
 *
 * @author Martin Desruisseaux
 */
final class NewWordDialog {
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
     * Creates a new dialog for the given entry.
     */
    NewWordDialog(final AugmentedEntry entry) {
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
        final TilePane buttons = new TilePane();
        confirm.setMaxWidth(120);
        cancel .setMaxWidth(120);
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

        final Stage ds = new Stage();
        ds.initModality(Modality.APPLICATION_MODAL);
        ds.setScene(new Scene(pane));
        ds.setResizable(false);
        ds.show();
    }
}
