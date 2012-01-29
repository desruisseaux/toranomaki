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
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.concurrent.Task;
import javafx.util.Callback;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.JMdict;


/**
 * Provides the functionalities for managing a table of words search result.
 *
 * @author Martin Desruisseaux
 */
final class WordTable implements EventHandler<ActionEvent>, AutoCloseable {
    /**
     * The dictionary to use for searching words.
     */
    private final JMdict dictionary;

    /**
     * The entries to show in the table.
     */
    private final ObservableList<Entry> entries;

    /**
     * The field where the user enter the word to search.
     */
    private final TextField search;

    /**
     * The executor for search services.
     */
    private final ExecutorService executor;

    /**
     * Creates a new instance using the given dictionary for searching words.
     */
    WordTable(final JMdict dictionary) {
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
        final TableView<Entry> table = new TableView<>(entries);
        final ObservableList<TableColumn<Entry, ?>> columns = table.getColumns();
        if (true) { // Kanji elements column
            final TableColumn<Entry,String> column = new TableColumn<>("Kanji");
            column.setCellValueFactory(new Callback<CellDataFeatures<Entry,String>, ObservableValue<String>>() {
                @Override public ObservableValue<String> call(final CellDataFeatures<Entry,String> cell) {
                    return new EntryValue.Kanji(cell.getValue());
                }
            });
            column.setPrefWidth(120);
            columns.add(column);
        }
        if (true) { // Reading elements column
            final TableColumn<Entry,String> column = new TableColumn<>("Reading");
            column.setCellValueFactory(new Callback<CellDataFeatures<Entry,String>, ObservableValue<String>>() {
                @Override public ObservableValue<String> call(final CellDataFeatures<Entry,String> cell) {
                    return new EntryValue.Reading(cell.getValue());
                }
            });
            column.setPrefWidth(200);
            columns.add(column);
        }
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
        final String word = search.getText().trim();
        final Task<Entry[]> task = new Task<Entry[]>() {
            @Override protected Entry[] call() throws SQLException {
                final Entry[] selected = dictionary.search(word);
                Platform.runLater(new Runnable() {
                    @Override public void run() {
                        entries.setAll(selected);
                    }
                });
                return selected;
            }
        };
        executor.execute(task);
        event.consume();
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
}
