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
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;

import javafx.embed.swing.JFXPanel;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;


/**
 * The entry point as a Swing application. Ideally we would make a pure JavaFX application.
 * However for now, we need to mix Swing and JavaFX because JavaFX does not yet support the
 * input methods for Kanji or Hiragana characters (at least on MacOS).
 *
 * @author Martin Desruisseaux
 */
public final class SwingMain extends WindowAdapter {
    /**
     * The text editor.
     */
    private final SwingEditor editor;

    /**
     * The result of word search.
     */
    private final WordTable table;

    /**
     * The object used for waiting the application termination.
     */
    private final CountDownLatch monitor = new CountDownLatch(1);

    /**
     * Launches the Toranomaki application.
     *
     * @param args The command lines arguments, which are ignored.
     * @throws IOException In an error occurred while getting the application directory.
     */
    public static void main(final String[] args) throws IOException {
        final JFrame frame = new JFrame("Toranomaki");
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final SwingMain main = new SwingMain(frame, executor);
            try {
                main.monitor.await();
            } finally {
                main.editor.save();
                executor.shutdown();
            }
        } catch (Throwable e) {
            Logging.possibleDataLost(e);
        } finally {
            frame.dispose();
        }
    }

    /**
     * Creates a new application.
     */
    private SwingMain(final JFrame frame, final ExecutorService executor) throws IOException {
        final Dictionary dictionary  = new Dictionary();
        final WordPanel   description = new WordPanel();
                          table       = new WordTable(description, dictionary, executor);
                          editor      = new SwingEditor(table);
        final JFXPanel    fxDesc      = new JFXPanel();
        final JFXPanel    fxTable     = new JFXPanel();
        final JSplitPane  split1      = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor.createPane(), fxTable);
        final JSplitPane  split2      = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fxDesc, split1);
        split1.setResizeWeight(1);
        split1.setDividerLocation(300);
        split2.setDividerLocation(100);
        frame.setSize(800, 600);
        frame.add(split2);
        frame.addWindowListener(this);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        Platform.runLater(new Runnable() {
            @Override public void run() {
                final StackPane stack = new StackPane();
                stack.getChildren().add(description.createPane());
                fxDesc.setScene(new Scene(stack));
                fxTable.setScene(new Scene(table.createPane(null)));
            }
        });
    }

    /**
     * Invoked in the Swing thread when the application is closing.
     *
     * @param event The event emitted by the frame to dispose.
     */
    @Override
    public void windowClosing(final WindowEvent event) {
        monitor.countDown();
    }
}
