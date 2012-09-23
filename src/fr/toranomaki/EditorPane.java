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
import java.util.concurrent.ExecutorService;

import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Control;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.geometry.Orientation;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.event.Event;


/**
 * The text editor, together with the table of word search results on the bottom.
 *
 * @author Martin Desruisseaux
 */
final class EditorPane extends EditorTextArea implements EventHandler<KeyEvent>, ChangeListener<Number> {
    /**
     * The panel showing a description of the selected word.
     */
    private final WordPanel description;

    /**
     * The editor area.
     */
    private final TextArea textArea;

    /**
     * {@code true} if a key from the keyboard is pressed and not yet released.
     */
    private transient boolean isKeyPressed;

    /**
     * The caret position from where to search for a entry in the dictionary.
     */
    private transient int caretPosition;

    /**
     * Creates a new instance using the given dictionary for searching words.
     */
    EditorPane(final Dictionary dictionary, final ExecutorService executor) {
        description = new WordPanel();
        textArea    = new TextArea();
        wordTable   = new WordTable(description, dictionary, executor);
        textArea.setStyle("-fx-font-size: " + WordPanel.HIRAGANA_SIZE + "pt;");
        try {
            final String text = load();
            if (text != null) {
                textArea.setText(text);
            }
        } catch (IOException e) {
            Logging.recoverableException(EditorPane.class, "load", e);
            // We can continue - the editor will just be initially empty.
        }
        textArea.caretPositionProperty().addListener(this);
        textArea.setOnKeyPressed (this);
        textArea.setOnKeyReleased(this);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Control createPane() {
        final Node desc = description.createPane();
        SplitPane.setResizableWithParent(desc, false);
        final SplitPane pane = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(desc, textArea, wordTable.createPane(null));
        pane.setDividerPositions(0.15, 0.6);
        return pane;
    }

    /**
     * Saves the editor content.
     */
    final void save() throws IOException {
        save(textArea.getText());
    }

    /**
     * Invoked when a key has been pressed or released.
     */
    @Override
    public void handle(final KeyEvent event) {
        final EventType<? extends Event> type = event.getEventType();
        if (KeyEvent.KEY_PRESSED.equals(type)) {
            isKeyPressed = true;
        } else if (KeyEvent.KEY_RELEASED.equals(type)) {
            isKeyPressed = false;
            searchWordAtCaret();
        }
    }

    /**
     * Invoked when the caret position changed. This method search the word starting
     * at the caret position, then update the table content accordingly.
     */
    @Override
    public void changed(final ObservableValue<? extends Number> property,
            final Number oldValue, final Number newValue)
    {
        caretPosition = newValue.intValue();
        if (!isKeyPressed) {
            searchWordAtCaret();
        }
    }

    /**
     * Searches the word at the caret position. This method must be invoked
     * from the JavaFX thread, and will launch the start in a background thread.
     */
    private void searchWordAtCaret() {
        final int position = caretPosition;
        caretPosition = -1;
        if (position >= 0) {
            final int docLength = textArea.getLength();
            final int length = Math.min(docLength - position, LONGUEST_KANJI_WORD);
            if (length > 0) {
                search(textArea.getText(position, position + length), position);
            }
        }
    }
}
