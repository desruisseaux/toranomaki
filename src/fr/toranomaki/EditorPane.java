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
import java.io.IOException;
import java.io.Writer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

import fr.toranomaki.edict.SearchResult;


/**
 * The text editor, together with the table of word search results on the bottom.
 *
 * @author Martin Desruisseaux
 */
final class EditorPane extends Data implements EventHandler<KeyEvent>, ChangeListener<Number> {
    /**
     * The approximative length of the longest entry in Kanji characters.
     */
    private static final int LONGUEST_KANJI_WORD = 16;

    /**
     * The panel showing a description of the selected word.
     */
    private final WordPanel description;

    /**
     * The editor area.
     */
    private final TextArea textArea;

    /**
     * The table of selected words. Also used in order to get a reference to the dictionary.
     */
    private final WordTable wordTable;

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
     * Returns the file in which to save the editor content.
     */
    private static File getFile() throws IOException {
        return getDirectory().resolve("Editor.txt").toFile();
    }

    /**
     * Loads the editor content from the last saved session.
     *
     * @return The text, or {@code null} if none.
     * @throws IOException If an error occurred while loading the text.
     */
    private static String load() throws IOException {
        final File file = getFile();
        if (file.isFile()) {
            final StringBuilder buffer = new StringBuilder(4096);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), FILE_ENCODING))) {
                String line; while ((line = in.readLine()) != null) {
                    buffer.append(line).append('\n');
                }
            }
            if (buffer.length() != 0 && buffer.charAt(0) == BYTE_ORDER_MARK) {
                return buffer.substring(1);
            }
            return buffer.toString();
        }
        return null;
    }

    /**
     * Saves the editor content.
     *
     * @throws IOException If an error occurred while saving the text.
     */
    final void save() throws IOException {
        final String text = textArea.getText();
        final File file = getFile();
        if (text.trim().length() == 0) {
            file.delete();
        } else {
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file), FILE_ENCODING)) {
                out.write(BYTE_ORDER_MARK);
                out.write(text);
            }
        }
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

    /**
     * Searches a word matching the given fragment. This method performs the search in a
     * background thread, then invoke {@link #searchCompleted(SearchResult)} when the search
     * is completed.
     *
     * @param part           The document fragment (not necessarily a word).
     * @param documentOffset Index of the first character of the given word in the document.
     *                       This information is not used by this method. This value is simply
     *                       stored in the {@link #documentOffset} field for caller convenience.
     */
    private void search(final String part, final int documentOffset) {
        wordTable.executor.execute(new Runnable() {
            @Override public void run() {
                final int stop = part.length();
                int lower = 0;
                while (lower < stop) { // Skip leading spaces without moving to next line.
                    final int c = part.codePointAt(lower);
                    if (!Character.isSpaceChar(c)) break;
                    lower += Character.charCount(c);
                }
                int upper = lower;
                while (upper < stop) {
                    final int c = part.codePointAt(upper);
                    if (!Character.isAlphabetic(c)) break;
                    upper += Character.charCount(c);
                }
                if (upper > lower) {
                    final SearchResult search = wordTable.dictionary.searchBest(part.substring(lower, upper));
                    if (search != null) {
                        search.documentOffset = documentOffset + lower;
                        searchCompleted(search);
                    }
                }
            }
        });
    }

    /**
     * Invoked when a search has been successfully completed.
     * The default implementation update the table view.
     */
    protected void searchCompleted(final SearchResult result) {
        try {
            wordTable.setContent(result.entries, result.selectedIndex);
        } catch (Throwable e) {
            Logging.recoverableException(WordTable.class, "setContent", e);
        }
    }
}
