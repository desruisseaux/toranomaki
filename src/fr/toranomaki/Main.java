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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URISyntaxException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import org.apache.derby.jdbc.EmbeddedDataSource;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.application.Application;
import javafx.geometry.Side;

import fr.toranomaki.edict.JMdict;


/**
 * The main application window.
 *
 * @author Martin Desruisseaux
 */
public final class Main extends Application {
    /**
     * The connection to the JMdict dictionary.
     */
    private EmbeddedDataSource dataSource;

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
     * Returns the application installation directory. This method returns the first of the
     * following choices:
     * <p>
     * <ul>
     *   <li>If the {@code "toranomaki.dir"} property is set, returns that directory.</li>
     *   <li>Otherwise if this application is bundled in a JAR file, returns the directory
     *       that contain the JAR file.</li>
     *   <li>Otherwise returns the user directory.</li>
     * </ul>
     * <p>
     * If every cases, this method verify that the directory exists before to return it.
     *
     * @return The application directory.
     * @throws IOException In an error occurred while getting the application directory.
     */
    public static File getDirectory() throws IOException {
        File file;
        final String directory = System.getProperty("toranomaki.dir");
        if (directory != null) {
            file = new File(directory);
        } else {
            URL url = Main.class.getResource("Main.class");
            if ("jar".equals(url.getProtocol())) {
                String path = url.getPath();
                path = path.substring(0, path.indexOf('!'));
                url = new URL(path);
                try {
                    file = new File(url.toURI());
                } catch (URISyntaxException e) {
                    throw new MalformedURLException(e.getLocalizedMessage());
                }
                file = file.getParentFile();
            } else {
                file = new File(System.getProperty("user.dir", "."));
            }
        }
        if (!file.isDirectory()) {
            throw new FileNotFoundException(file.getPath());
        }
        return file;
    }

    /**
     * Returns the data source to use for the connection to the SQL database.
     *
     * @return The data source.
     * @throws IOException In an error occurred while getting the application directory.
     */
    public static EmbeddedDataSource getDataSource() throws IOException {
        final EmbeddedDataSource datasource = new EmbeddedDataSource();
        datasource.setDatabaseName(new File(getDirectory(), "JMdict").getPath().replace(File.separatorChar, '/'));
        datasource.setDataSourceName("JMdict"); // Optional - for information purpose only.
        return datasource;
    }

    /**
     * Connects the application to the database.
     *
     * @throws IOException In an error occurred while getting the application directory.
     * @throws SQLException If an error occurred while connecting to the database.
     */
    @Override
    public void init() throws IOException, SQLException {
        dataSource = getDataSource();
        final JMdict dictionary = new JMdict(dataSource);
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
        dataSource.setShutdownDatabase("shutdown");
        try {
            dataSource.getConnection().close();
        } catch (SQLException e) {
            // This is the expected exception.
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
        final TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setSide(Side.LEFT);

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
