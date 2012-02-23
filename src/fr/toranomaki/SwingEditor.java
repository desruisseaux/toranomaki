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


/**
 * The editor text area. We have to use a Swing component for now, because as of JavaFX 2.1-b13,
 * {@link javafx.scene.control.TextArea} control does not yet offer enough functionalities (input
 * framework not enabled, no highlight).
 *
 * @author Martin Desruisseaux
 */
@SuppressWarnings("serial")
final class SwingEditor extends KeyAdapter implements UndoableEditListener, CaretListener, Runnable {
    /**
     * The encoding of the file to be saved.
     */
    private static final String FILE_ENCODING = "UTF-8";

    /**
     * The approximative length of the longest word in Kanji characters.
     */
    private static final int LONGUEST_KANJI_WORD = 16;

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
     * The caret position from where to search for a word in the dictionary.
     */
    private transient int caretPosition;

    /**
     * Creates a new editor.
     */
    private SwingEditor() {
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
    }

    /**
     * Shows this widget.
     */
    static void show() {
        EventQueue.invokeLater(new Runnable() {
            @Override public void run() {
                for (final Window window : Window.getWindows()) {
                    if ("Toranomaki".equals(window.getName())) {
                        window.setVisible(true);
                        return;
                    }
                }
                final JFrame frame = new JFrame("Toranomaki editor");
                frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                frame.add(new JScrollPane(new SwingEditor().textPane,
                        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
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
    void load() throws IOException, BadLocationException {
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
    void save() throws IOException, BadLocationException {
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
     * When a key is pressed, do not search for words until the key is released.
     * The main purpose is to avoid continuous searches while the user is moving
     * the caret using the keyboard arrows.
     */
    @Override
    public void keyPressed(final KeyEvent event) {
        isKeyPressed = true;
    }

    /**
     * When the key is released, searches for the word.
     */
    @Override
    public void keyReleased(final KeyEvent event) {
        isKeyPressed = false;
        EventQueue.invokeLater(this);
    }

    /**
     * Invoked when the caret moved. Gets the caret position, and looks later for the word
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
     * Searches for a word now. This method must be run in the Swing thread.
     */
    @Override
    public void run() {
        final int position = caretPosition;
        if (position >= 0) {
            caretPosition = -1;
            isInternalEdit = true;
            final int docLength = document.getLength();
            try {
                // If a word was highlighted, make it appears as a normal text.
                // (... TODO ...)
                // Now get the text, and looks for it in the dictionary.
                final int length = Math.min(docLength - position, LONGUEST_KANJI_WORD);
                if (length > 0) try {
                    final String part = document.getText(position, length);
                    final int stop = part.length();
                    int lower=0;
                    while (lower<stop && Character.isWhitespace(part.charAt(lower))) lower++;
                    int upper=lower;
                    while (upper<stop && !Character.isWhitespace(part.charAt(upper))) upper++;
                    if (upper > lower) {
                        // (... TODO ...)
                    }
                } catch (BadLocationException e) {
                    // Should not happen. But if it does anyway, let the search be null.
                }
            } finally {
                isInternalEdit = false;
            }
        }
        // (... TODO ...)
    }
}
