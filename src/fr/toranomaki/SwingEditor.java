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

import fr.toranomaki.edict.SearchResult;


/**
 * The editor text area. We have to use a Swing component for now, because as of JavaFX 2.1-b13,
 * {@link javafx.scene.control.TextArea} control does not yet offer enough functionalities (input
 * framework not enabled, no highlight).
 *
 * @author Martin Desruisseaux
 */
final class SwingEditor extends EditorTextArea implements KeyListener, UndoableEditListener, CaretListener {
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
    SwingEditor(final WordTable wordTable) {
        super(wordTable);
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
            final String text = load();
            if (text != null) {
                document.insertString(0, text, null);
            }
        } catch (IOException | BadLocationException e) {
            Logging.recoverableException(SwingEditor.class, "load", e);
            // We can continue - the editor will just be initially empty.
        }
    }

    /**
     * Creates the swing panel.
     */
    final JComponent createPane() {
        return new JScrollPane(textPane,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    /**
     * Saves the editor content.
     */
    private void save() throws IOException, BadLocationException {
        save(document.getText(0, document.getLength()));
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
        undo.addEdit(event.getEdit());
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
        searchWordAtCaret();
    }

    /**
     * Invoked when the caret moved. Gets the caret position, and looks later for the entry
     * starting at that position.
     */
    @Override
    public void caretUpdate(final CaretEvent event) {
        caretPosition = Math.min(event.getMark(), event.getDot());
        if (!isKeyPressed) {
            searchWordAtCaret();
        }
    }

    /**
     * Searches the word at the caret position.
     * This method must be invoked from the Swing thread.
     */
    private void searchWordAtCaret() {
        final int position = caretPosition;
        if (position >= 0) {
            caretPosition = -1;
            final int docLength = document.getLength();
            final int length = Math.min(docLength - position, LONGUEST_KANJI_WORD);
            if (length > 0) try {
                search(document.getText(position, length), position);
            } catch (BadLocationException e) {
                // TODO: We need a better handling, maybe with a widget for bug reports.
                // For now, we let the search result to null.
                Logging.recoverableException(SwingEditor.class, "run", e);
            }
        }
    }

    /**
     * Invoked when a search has been successfully completed.
     */
    @Override
    void searchCompleted(final SearchResult result) {
        super.searchCompleted(result);
        EventQueue.invokeLater(new Runnable() {
            @Override public void run() {
                setSearchResult(result);
            }
        });
    }

    /**
     * Sets the search result.
     * Must be invoked in the Swing thread.
     */
    final void setSearchResult(final SearchResult result) {
        if (result != previousSearch) {
            if (previousSearch != null) {
                highlighter.removeHighlight(previousSearch.highlighterKey);
            }
            previousSearch = result;
            if (result != null) try {
                final int base = result.documentOffset;
                result.highlighterKey = highlighter.addHighlight(base, base + result.matchLength,
                        result.isFullMatch ? (result.isDerivedWord ? emphaseDerived : emphaseComplete) : emphase);
            } catch (BadLocationException e) {
                // TODO: We need a better handling, maybe with a widget for bug reports.
                // For now, we let the search result to null.
                Logging.recoverableException(SwingEditor.class, "setSearchResult", e);
            }
        }
    }
}
