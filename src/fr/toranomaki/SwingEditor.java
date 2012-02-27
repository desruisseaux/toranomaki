/*
 *    Toranomaki - Help with Japanese words using the EDICT dictionary.
 *    (C) 2011-2012, Martin Desruisseaux
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

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import java.sql.SQLException;

import fr.toranomaki.edict.JMdict;
import fr.toranomaki.edict.SearchResult;
import static fr.toranomaki.edict.JMdict.MINIMAL_SEARCH_LENGTH;


/**
 * The editor text area. We have to use a Swing component for now, because as of JavaFX 2.1-b13,
 * {@link javafx.scene.control.TextArea} control does not yet offer enough functionalities (input
 * framework not enabled, no highlight).
 *
 * @author Martin Desruisseaux
 */
@SuppressWarnings("serial")
final class SwingEditor extends WindowAdapter implements KeyListener, UndoableEditListener, CaretListener, Runnable {
    /**
     * The encoding of the file to be saved.
     */
    private static final String FILE_ENCODING = "UTF-8";

    /**
     * The approximative length of the longest entry in Kanji characters.
     */
    private static final int LONGUEST_KANJI_WORD = 16;

    /**
     * The table of selected words. Also used in order to get a reference to the dictionary.
     */
    private final WordTable wordTable;

    /**
     * The editor component.
     */
    private final JEditorPane textPane;

    /**
     * The edited document.
     */
    private final Document document;

    /**
     * The manager for undoing editions.
     */
    private final UndoManager undo;

    /**
     * The previous result of entry search, or {@code null} if none.
     * Used in order to remove the highlight.
     */
    private transient SearchResult previousSearch;

    /**
     * The highlighter provided by the text editor.
     */
    private final Highlighter highlighter;

    /**
     * The painter to use for emphasing words that are known to the dictionary.
     */
    private final Highlighter.HighlightPainter emphase, emphaseComplete, emphaseDerived;

    /**
     * {@code true} if the current edit operation should not be saved in the list
     * of undoable edit operations.
     */
    private transient boolean isInternalEdit;

    /**
     * {@code true} if a key from the keyboard is pressed and not yet released.
     */
    private transient boolean isKeyPressed;

    /**
     * The caret position from where to search for a entry in the dictionary.
     */
    private transient int caretPosition;

    /**
     * Creates a new editor.
     */
    private SwingEditor(final WordTable wordTable) {
        this.wordTable = wordTable;
        textPane = new JEditorPane();
        textPane.setContentType("text/plain");
        textPane.setFont(Font.decode("SansSerif-18"));
        textPane.addCaretListener(this);
        textPane.addKeyListener(this);

        emphase         = new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
        emphaseComplete = new DefaultHighlighter.DefaultHighlightPainter(new Color(192, 255, 192));
        emphaseDerived  = new DefaultHighlighter.DefaultHighlightPainter(Color.CYAN);
        undo            = new UndoManager();
        highlighter     = textPane.getHighlighter();
        document        = textPane.getDocument();
        document.addUndoableEditListener(this);

        final InputMap  inputMap  = textPane.getInputMap();
        final ActionMap actionMap = textPane.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Event.META_MASK), "undo");
        actionMap.put("undo", new UndoAction(false));
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Event.META_MASK), "redo");
        actionMap.put("redo", new UndoAction(true));
        try {
            load();
        } catch (IOException | BadLocationException e) {
            Logging.recoverableException(SwingEditor.class, "load", e);
            // We can continue - the editor will just be initially empty.
        }
    }

    /**
     * Invoked when the editor window is in the process of being closed.
     * This method saves the editor content.
     *
     * @todo This method is unreliable, since it is not invoked if the user close the
     *       application without closing the editor window first. We do not bother to
     *       fix this issue for now, since this class is a temporary hack to be removed
     *       when the JavaFX TextArea component will be more usable for our needs.
     *
     * @param event Ignored.
     */
    @Override
    public void windowClosing(final WindowEvent event) {
        try {
            save();
        } catch (IOException | BadLocationException e) {
            Logging.recoverableException(SwingEditor.class, "save", e);
            // DANGER - editor content is lost. Continue closing anyway.
            // We may revisit this behavior later if we have some widget
            // for reporting errors.
        }
    }

    /**
     * Shows this widget.
     */
    static void show(final WordTable wordTable) {
        EventQueue.invokeLater(new Runnable() {
            @Override public void run() {
                for (final Window window : Window.getWindows()) {
                    if ("Toranomaki".equals(window.getName())) {
                        window.setVisible(true);
                        return;
                    }
                }
                final SwingEditor editor = new SwingEditor(wordTable);
                final JFrame frame = new JFrame("Toranomaki editor");
                frame.add(new JScrollPane(editor.textPane,
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
                frame.addWindowListener(editor);
                frame.setName("Toranomaki");
                frame.setSize(800, 600);
                frame.setLocationByPlatform(true);
                frame.setVisible(true);
            }
        });
    }

    /**
     * Returns the file in which to save the editor content.
     */
    private File getFile() throws IOException {
        return new File(Main.getDirectory(), "Editor.txt");
    }

    /**
     * Loads the editor content.
     */
    private void load() throws IOException, BadLocationException {
        final File file = getFile();
        if (file.isFile()) {
            final StringBuilder buffer = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), FILE_ENCODING))) {
                String line; while ((line = in.readLine()) != null) {
                    buffer.append(line).append('\n');
                }
            }
            document.insertString(0, buffer.toString(), null);
        }
    }

    /**
     * Saves the editor content.
     */
    private void save() throws IOException, BadLocationException {
        final String content = document.getText(0, document.getLength());
        final File file = getFile();
        if (content.trim().length() == 0) {
            file.delete();
        } else {
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file), FILE_ENCODING)) {
                out.write(content);
            }
        }
    }

    /**
     * The action for doing or undoing an edit.
     */
    @SuppressWarnings("serial")
    private final class UndoAction extends AbstractAction {
        /** {@true} for redo instead than undo. */
        private final boolean redo;

        UndoAction(final boolean redo) {
            this.redo = redo;
        }

        @Override
        public void actionPerformed(final ActionEvent event) {
            final UndoManager undo = SwingEditor.this.undo;
            if (redo) {if (undo.canRedo()) undo.redo();}
            else      {if (undo.canUndo()) undo.undo();}
        }
    }

    /**
     * Remember the edition that just occurred, so we can undo it.
     */
    @Override
    public void undoableEditHappened(final UndoableEditEvent event) {
        if (!isInternalEdit) {
            undo.addEdit(event.getEdit());
        }
    }

    /**
     * Ignored.
     */
    @Override
    public void keyTyped(final KeyEvent event) {
    }

    /**
     * When a key is pressed, do not search for words until the key is released.
     * The main purpose is to avoid continuous searches while the user is moving
     * the caret using the keyboard arrows.
     */
    @Override
    public void keyPressed(final KeyEvent event) {
        isKeyPressed = true;
    }

    /**
     * When the key is released, searches for the entry.
     */
    @Override
    public void keyReleased(final KeyEvent event) {
        isKeyPressed = false;
        EventQueue.invokeLater(this);
    }

    /**
     * Invoked when the caret moved. Gets the caret position, and looks later for the entry
     * starting at that position.
     */
    @Override
    public void caretUpdate(final CaretEvent event) {
        caretPosition = Math.min(event.getMark(), event.getDot());
        if (!isKeyPressed) {
            EventQueue.invokeLater(this);
        }
    }

    /**
     * Searches for a entry now. This method must be run in the Swing thread.
     *
     * @todo Needs to move the database operation outside from the Swing thread.
     */
    @Override
    public void run() {
        final int position = caretPosition;
        if (position >= 0) {
            caretPosition = -1;
            isInternalEdit = true;
            final int docLength = document.getLength();
            try {
                // If a entry was highlighted, make it appears as a normal text.
                SearchResult search = previousSearch;
                if (search != null) {
                    previousSearch = null;
                    highlighter.removeHighlight(search.highlighterKey);
                }
                // Now get the text, and looks for it in the dictionary.
                final int length = Math.min(docLength - position, LONGUEST_KANJI_WORD);
                if (length >= MINIMAL_SEARCH_LENGTH) try {
                    final String part = document.getText(position, length);
                    final int stop = part.length();
                    int lower = 0;
                    while (lower < stop) { // Skip leading spaces.
                        final int c = part.codePointAt(lower);
                        if (!Character.isWhitespace(c)) break;
                        lower += Character.charCount(c);
                    }
                    int upper = lower;
                    while (upper < stop) { // Stop at the first space.
                        final int c = part.codePointAt(upper);
                        if (Character.isWhitespace(c)) break;
                        upper += Character.charCount(c);
                    }
                    if (upper - lower >= MINIMAL_SEARCH_LENGTH) {
                        search = wordTable.dictionary.searchBest(part.substring(lower, upper));
                        if (search != null) {
                            final int base = position + lower;
                            search.highlighterKey = highlighter.addHighlight(base, base + search.matchLength,
                                    search.isFullMatch ? (search.isDerivedWord ? emphaseDerived : emphaseComplete) : emphase);
                            previousSearch = search;
                            wordTable.setContent(search.word);
                        }
                    }
                } catch (SQLException | BadLocationException e) {
                    // TODO: We need a better handling, maybe with a widget for bug reports.
                    // For now, we let the search result to null.
                    Logging.recoverableException(JMdict.class, "searchBest", e);
                }
            } finally {
                isInternalEdit = false;
            }
        }
    }
}
