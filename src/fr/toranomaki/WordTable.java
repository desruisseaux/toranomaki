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

import java.util.concurrent.ExecutorService;

import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.concurrent.Task;

import fr.toranomaki.edict.Alphabet;
import fr.toranomaki.edict.DictionaryReader;
import fr.toranomaki.grammar.AugmentedEntry;
import fr.toranomaki.grammar.CharacterType;


/**
 * A table of words. The content of this table can be specified explicitely by a call to
 * {@link #setContent(Entry[], int)}, or can be set to the result of a search in the whole
 * dictionary by a call to {@link #setContent(String)}.
 *
 * @author Martin Desruisseaux
 */
final class WordTable implements EventHandler<ActionEvent>, ListChangeListener<TablePosition<AugmentedEntry,AugmentedEntry>> {
    /**
     * The dictionary to use for searching words.
     */
    final DictionaryReader dictionary;

    /**
     * The entries to show in the table.
     */
    private final ObservableList<AugmentedEntry> entries;

    /**
     * The field where the user enter the word to search.
     */
    private final TextField search;

    /**
     * The executor for search services.
     */
    final ExecutorService executor;

    /**
     * The panel to notify when a new word is selected in the table.
     */
    private final WordPanel description;

    /**
     * Creates a new instance using the given dictionary for searching words.
     *
     * @param description The word panel where to show the description of the selected word.
     * @param dictionary  The dictionary to use for searching words.
     * @param executor    The executor to use for performing searches in a background thread.
     */
    WordTable(final WordPanel description, final DictionaryReader dictionary, final ExecutorService executor) {
        this.description = description;
        this.dictionary  = dictionary;
        this.executor    = executor;
        entries = FXCollections.observableArrayList();
        search = new TextField();
        search.setPromptText("Search word");
        search.setOnAction(this);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    Pane createPane() {
        final TableView<AugmentedEntry> table = new TableView<>(entries);
        final ObservableList<TableColumn<AugmentedEntry, ?>> columns = table.getColumns();
        final EntryCellValue.DefaultFactory defaultFactory = new EntryCellValue.DefaultFactory();
        if (true) { // Kanji elements column
            final TableColumn<AugmentedEntry,AugmentedEntry> column = new TableColumn<>("Kanji");
            column.setCellFactory(new WordCellFactory(true));
            column.setCellValueFactory(defaultFactory);
            column.setPrefWidth(120);
            columns.add(column);
        }
        if (true) { // Reading elements column
            final TableColumn<AugmentedEntry,AugmentedEntry> column = new TableColumn<>("Reading");
            column.setCellFactory(new WordCellFactory(false));
            column.setCellValueFactory(defaultFactory);
            column.setPrefWidth(200);
            columns.add(column);
        }
        if (true) { // Part Of Speech column
            final TableColumn<AugmentedEntry,String> column = new TableColumn<>("Type");
            column.setCellValueFactory(new EntryCellValue.SenseFactory(true));
            column.setPrefWidth(70);
            columns.add(column);
        }
        if (true) { // Sense elements column
            final TableColumn<AugmentedEntry,String> column = new TableColumn<>("Sense");
            column.setCellValueFactory(new EntryCellValue.SenseFactory(false));
            column.setPrefWidth(300);
            columns.add(column);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        final ListChangeListener<TablePosition> listener = (ListChangeListener) this;
        table.getSelectionModel().getSelectedCells().addListener(listener);
        final BorderPane pane = new BorderPane();
        pane.setCenter(table);
        pane.setBottom(search);
        return pane;
    }

    /**
     * Invoked when the user press {@code Enter} in the search field.
     * This method do the search and update the table with the result.
     */
    @Override
    public void handle(final ActionEvent event) {
        setContent(search.getText().trim());
        event.consume();
    }

    /**
     * Sets the table content to the result of the search for the given word.
     * This method must be invoked from the JavaFX thread. It will start the
     * search in a background thread, then will invoke {@link #setContent(Entry[], int)}
     * after the search has been completed.
     *
     * @param word The word to search.
     */
    final void setContent(final String word) {
        final CharacterType type = CharacterType.forWord(word);
        final Alphabet alphabet = (type != null) ? type.alphabet : null;
        final Task<AugmentedEntry[]> task = new Task<AugmentedEntry[]>() {
            @Override
            protected AugmentedEntry[] call() {
                final AugmentedEntry[] tableEntries;
                try {
                    tableEntries = dictionary.getEntriesUsingPrefix(alphabet, word);
                    setContent(tableEntries, -1);
                } catch (Throwable e) {
                    Logging.recoverableException(WordTable.class, "setContent", e);
                    return null;
                }
                return tableEntries;
            }
        };
        executor.execute(task);
    }

    /**
     * Sets the table content to the given entries.
     * This method can be invoked from any thread.
     *
     * @param tableEntries The entries to show in the table.
     * @param selectedIndex The index of the entry to show in the description area, or -1 if none.
     */
    final void setContent(final AugmentedEntry[] tableEntries, final int selectedIndex) {
        final AugmentedEntry selected = (selectedIndex >= 0) ? tableEntries[selectedIndex] : null;
        if (Platform.isFxApplicationThread()) {
            entries.setAll(tableEntries);
            description.setSelected(selected);
        } else {
            Platform.runLater(new Runnable() {
                @Override public void run() {
                    entries.setAll(tableEntries);
                    description.setSelected(selected);
                }
            });
        }
    }

    /**
     * Invoked when a new row has been selected.
     *
     * @param change The change.
     */
    @Override
    public void onChanged(final Change<? extends TablePosition<AugmentedEntry,AugmentedEntry>> change) {
        AugmentedEntry selected = null;
        while (change.next()) {
            if (change.wasAdded()) {
                int index = change.getFrom();
                if (index >= 0) {
                    index = change.getList().get(index).getRow();
                    if (index < entries.size()) {
                        selected = entries.get(index);
                        break;
                    }
                }
            }
        }
        description.setSelected(selected);
    }
}
