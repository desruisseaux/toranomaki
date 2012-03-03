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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
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

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.JMdict;


/**
 * Provides the functionalities for managing a table of words search result.
 *
 * @author Martin Desruisseaux
 */
final class WordTable implements AutoCloseable, EventHandler<ActionEvent>,
        ListChangeListener<TablePosition<WordElement,WordElement>>
{
    /**
     * The dictionary to use for searching words.
     */
    final JMdict dictionary;

    /**
     * The entries to show in the table.
     */
    private final ObservableList<WordElement> entries;

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
     */
    WordTable(final WordPanel description, final JMdict dictionary) {
        this.description = description;
        this.dictionary = dictionary;
        executor = Executors.newSingleThreadExecutor();
        entries = FXCollections.observableArrayList();
        search = new TextField();
        search.setPromptText("Search word");
        search.setOnAction(this);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    Pane createPane() {
        final TableView<WordElement> table = new TableView<>(entries);
        final ObservableList<TableColumn<WordElement, ?>> columns = table.getColumns();
        final WordElementValue.DefaultFactory defaultFactory = new WordElementValue.DefaultFactory();
        if (true) { // Kanji elements column
            final TableColumn<WordElement,WordElement> column = new TableColumn<>("Kanji");
            column.setCellFactory(new WordCellFactory(true));
            column.setCellValueFactory(defaultFactory);
            column.setPrefWidth(120);
            columns.add(column);
        }
        if (true) { // Reading elements column
            final TableColumn<WordElement,WordElement> column = new TableColumn<>("Reading");
            column.setCellFactory(new WordCellFactory(false));
            column.setCellValueFactory(defaultFactory);
            column.setPrefWidth(200);
            columns.add(column);
        }
        if (true) { // Part Of Speech column
            final TableColumn<WordElement,String> column = new TableColumn<>("Type");
            column.setCellValueFactory(new WordElementValue.SenseFactory(true));
            column.setPrefWidth(70);
            columns.add(column);
        }
        if (true) { // Sense elements column
            final TableColumn<WordElement,String> column = new TableColumn<>("Sense");
            column.setCellValueFactory(new WordElementValue.SenseFactory(false));
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
     * This method should be invoked from a background thread.
     *
     * @param word The word to search.
     */
    final void setContent(final String word) {
        final Task<WordElement[]> task = new Task<WordElement[]>() {
            @Override
            protected WordElement[] call() throws SQLException {
                final WordElement[] selected;
                try {
                    selected = setContent(dictionary.search(word), -1);
                } catch (Throwable e) {
                    Logging.recoverableException(WordTable.class, "setContent", e);
                    return null;
                }
                return selected;
            }
        };
        executor.execute(task);
    }

    /**
     * Sets the table content to the given entries.
     * This method should be invoked from a background thread.
     *
     * @param tableEntries The entries to show in the table.
     * @param selectedIndex The index of the entry to show in the description area, or -1 if none.
     */
    final WordElement[] setContent(final Entry[] tableEntries, final int selectedIndex) throws SQLException {
        final WordElement[] elements = new WordElement[tableEntries.length];
        for (int i=0; i<tableEntries.length; i++) {
            elements[i] = new WordElement(dictionary, tableEntries[i]);
        }
        final WordElement selected = (selectedIndex >= 0) ? elements[selectedIndex] : null;
        Platform.runLater(new Runnable() {
            @Override public void run() {
                entries.setAll(elements);
                description.setSelected(selected);
            }
        });
        return elements;
    }

    /**
     * Closes the resources used by this words table. Note that this method closes also
     * the connection to the SQL database, which is shared by the {@link Training} pane.
     */
    @Override
    public void close() throws SQLException {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            /*
             * Someone doesn't want to let us sleep. Go close the database connection.
             * Note that it may cause a SQLException in the thread that we failed to shutdown.
             */
        }
        dictionary.close();
    }

    /**
     * Invoked when a new row has been selected.
     *
     * @param change The change.
     */
    @Override
    public void onChanged(final Change<? extends TablePosition<WordElement, WordElement>> change) {
        WordElement selected = null;
        while (change.next()) {
            if (change.wasAdded()) {
                final int index = change.getFrom();
                if (index >= 0) {
                    selected = entries.get(change.getList().get(index).getRow());
                    break;
                }
            }
        }
        description.setSelected(selected);
    }
}
