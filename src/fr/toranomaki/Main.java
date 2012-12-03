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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.input.KeyCombination;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.application.Application;


/**
 * The main application entry point.
 *
 * @author Martin Desruisseaux
 */
public final class Main extends Application {
    /**
     * The application-wide dictionary.
     */
    private Dictionary dictionary;

    /**
     * Controls the panel used for vocabulary training.
     */
    private TrainingPane training;

    /**
     * Controls the editor pane.
     */
    private EditorPane editor;

    /**
     * The executor to use for performing searches in a background thread.
     */
    private ExecutorService executor;

    /**
     * Launches the Toranomaki application.
     *
     * @param args the command line arguments.
     */
    public static void main(String[] args) {
        launch(Main.class, args);
    }

    /**
     * Creates a new application.
     */
    public Main() {
    }

    /**
     * Connects the application to the dictionary file.
     *
     * @throws IOException In an error occurred while opening the dictionary.
     */
    @Override
    public void init() throws IOException {
        dictionary = new Dictionary();
        executor   = Executors.newSingleThreadExecutor();
        training   = new TrainingPane(dictionary, executor);
        editor     = new EditorPane  (dictionary, executor);
    }

    /**
     * Releases the resources used by this application.
     */
    @Override
    public void stop() {
        executor.shutdown();
        try {
            editor.save();
            dictionary.save();
        } catch (IOException e) {
            Logging.possibleDataLost(e);
        }
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            /*
             * Someone doesn't want to let us sleep.
             */
        }
    }

    /**
     * The action executed when the user switch window.
     */
    private static final class ShowWindow implements EventHandler<ActionEvent> {
        final BorderPane pane;
        final Node view;

        ShowWindow(final BorderPane pane, final Node view) {
            this.pane = pane;
            this.view = view;
        }

        @Override public void handle(final ActionEvent e) {
            pane.setCenter(view);
        }
    }

    /**
     * Creates and show the Graphical User Interface (GUI).
     *
     * @param stage The window where to display the GUI.
     */
    @Override
    public void start(final Stage stage) {
        stage.setTitle("Toranomaki");
        final Node initial = training.createPane();
        final BorderPane pane = new BorderPane();
        pane.setCenter(initial);

        final MenuBar bar = new MenuBar();
        {
            final Menu menu = new Menu("Edit");
            {
                final MenuItem menuItem = new MenuItem("Annotate");
                menuItem.setOnAction(new EventHandler<ActionEvent>() {
                    @Override public void handle(final ActionEvent event) {
                        editor.annotateSelectedText();
                    }
                });
                menu.getItems().add(menuItem);
            }
            bar.getMenus().add(menu);
        }
        {
            final Menu menu = new Menu("Window");
            {
                final MenuItem menuItem = new MenuItem("Training");
                menuItem.setOnAction(new ShowWindow(pane, initial));
                menuItem.setAccelerator(KeyCombination.valueOf("Shortcut+T"));
                menu.getItems().add(menuItem);
            }
            {
                final MenuItem menuItem = new MenuItem("Editor");
                menuItem.setOnAction(new ShowWindow(pane, editor.createPane()));
                menuItem.setAccelerator(KeyCombination.valueOf("Shortcut+E"));
                menu.getItems().add(menuItem);
            }
            bar.getMenus().add(menu);
        }
        bar.setUseSystemMenuBar(true);
        pane.setTop(bar);

        stage.setScene(new Scene(pane, 800, 600));
        stage.show();
    }
}
