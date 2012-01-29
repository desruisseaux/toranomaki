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
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.application.Application;

import fr.toranomaki.edict.JMdict;


/**
 * The main application window.
 *
 * @author Martin Desruisseaux
 */
public final class Main extends Application {
    /**
     * Controls the panel used for vocabulary training.
     */
    private Training training;

    /**
     * Controls the editor pane.
     */
    private Editor editor;

    /**
     * Launch the Toranomaki application.
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
     * Connects the application to the database.
     *
     * @throws SQLException If an error occurred while connecting to the database.
     */
    @Override
    public void init() throws SQLException {
        final JMdict dictionary = new JMdict();
        training = new Training(dictionary);
        editor = new Editor(dictionary);
    }

    /**
     * Releases the resources used by this application (database connection, service threads).
     *
     * @throws SQLException If an error occurred while closing the connection to the database.
     */
    @Override
    public void stop() throws SQLException {
        editor.table.close();
    }

    /**
     * Creates and show the Graphical User Interface (GUI).
     *
     * @param stage The window where to display the GUI.
     */
    @Override
    public void start(final Stage stage) {
        stage.setTitle("Toranomaki");
        final TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tab = new Tab("Vocabulary");
        tab.setContent(training.createPane());
        tabs.getTabs().add(tab);

        tab = new Tab("Editor");
        tab.setContent(editor.createPane());
        tabs.getTabs().add(tab);

        stage.setScene(new Scene(tabs, 800, 600));
        stage.show();
    }
}
