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
import java.sql.SQLException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javafx.embed.swing.JFXPanel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;


/**
 * The entry point as a Swing application. Ideally we would make a pure JavaFX application.
 * However for now, we need to mix Swing and JavaFX because JavaFX does not yet support the
 * input methods for Kanji or Hiragana characters (at least on MacOS).
 *
 * @author Martin Desruisseaux
 */
public final class SwingMain extends WindowAdapter implements Runnable, EventHandler<ActionEvent> {
    /**
     * The JavaFX application.
     */
    private final Main application;

    /**
     * The panel to construct in the {@link #run()} method.
     */
    private final JFXPanel fxPanel;

    /**
     * For internal usage by {@link #main(String[])} only.
     */
    private SwingMain() {
        application = new Main();
        fxPanel     = new JFXPanel();
    }

    /**
     * Launches the Toranomaki application.
     *
     * @param args The command lines arguments, which are ignored.
     * @throws IOException In an error occurred while getting the application directory.
     * @throws SQLException If an error occurred while connecting to the database.
     */
    public static void main(final String[] args) throws IOException, SQLException {
        final JFrame    frame   = new JFrame("Toranomaki");
        final SwingMain main    = new SwingMain();
        frame.addWindowListener(main);
        frame.add(main.fxPanel);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        main.application.init();
        Platform.runLater(main);
    }

    /**
     * Invoked in the JavaFX thread for building the JavaFX components.
     * This method is public as an implementation side-effect and should be ignored.
     */
    @Override
    public void run() {
        fxPanel.setScene(application.createScene(this));
    }

    /**
     * Invoked in the JavaFX thread when the user asked to Swing editor window.
     *
     * @param event The JavaFX event (ignored).
     */
    @Override
    public void handle(final ActionEvent event) {
        SwingEditor.show();
    }

    /**
     * Invoked in the Swing thread when the application is closing.
     *
     * @param event The event emitted by the frame to dispose.
     */
    @Override
    public void windowClosing(final WindowEvent event) {
        try {
            application.stop();
        } catch (SQLException e) {
            Logging.recoverableException(Main.class, "stop", e);
        }
        ((JFrame) event.getSource()).dispose();
        System.exit(0);
    }
}
