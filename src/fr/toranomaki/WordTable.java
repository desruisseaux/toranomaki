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
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
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
final class WordTable implements EventHandler<ActionEvent>, AutoCloseable {
    /**
     * The dictionary to use for searching words.
     */
    private final JMdict dictionary;

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
        final Task<WordElement[]> task = new Task<WordElement[]>() {
            @Override @SuppressWarnings("unchecked")
            protected WordElement[] call() throws SQLException {
                final WordElement[] selected;
                try {
                    final Entry[] entries = dictionary.search(word);
                    selected = new WordElement[entries.length];
                    for (int i=0; i<entries.length; i++) {
                        selected[i] = new WordElement(dictionary, entries[i]);
                    }
                } catch (Throwable e) {
                    e.printStackTrace(); // TODO
                    return null;
                }
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
