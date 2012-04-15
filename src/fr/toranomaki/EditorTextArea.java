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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.Writer;
import java.io.IOException;
import java.sql.SQLException;

import fr.toranomaki.edict.SearchResult;
import static fr.toranomaki.edict.DictionaryFile.getDirectory;


/**
 * The editor text area. In a future version, this class would contains everything needed for
 * the JavaFX editor. However for now, it is just a base class for the {@link SwingEditor} hack.
 *
 * @author Martin Desruisseaux
 */
class EditorTextArea {
    /**
     * The encoding of the file to be saved.
     */
    private static final String FILE_ENCODING = "UTF-8";

    /**
     * The <cite>byte order mark</cite> used to signal endianness of UTF-8 text files. This mark
     * is also used for indicating to softwares (Notepad on Windows, TextEdit of MacOS) that the
     * file is encoded in UTF-8.
     * <p>
     * The Unicode value is {@value}. The corresponding bytes sequence is
     * {@code 0xEF}, {@code 0xBB}, {@code 0xBF}.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Byte_order_mark">Byte order mark on Wikipedia</a>
     */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    /**
     * The approximative length of the longest entry in Kanji characters.
     */
    static final int LONGUEST_KANJI_WORD = 16;

    /**
     * The table of selected words. Also used in order to get a reference to the dictionary.
     */
    private final WordTable wordTable;

    /**
     * Creates a new editor.
     */
    EditorTextArea(final WordTable wordTable) {
        this.wordTable = wordTable;
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
    static String load() throws IOException {
        final File file = getFile();
        if (file.isFile()) {
            final StringBuilder buffer = new StringBuilder();
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
     * @param  text The content to save.
     * @throws IOException If an error occurred while saving the text.
     */
    static void save(final String text) throws IOException {
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
     * Searches a word matching the given fragment. This method performs the search in a
     * background thread, then invoke {@link #searchCompleted(SearchResult)} when the search
     * is completed.
     *
     * @param part           The document fragment (not necessarily a word).
     * @param documentOffset Index of the first character of the given word in the document.
     *                       This information is not used by this method. This value is simply
     *                       stored in the {@link #documentOffset} field for caller convenience.
     */
    final void search(final String part, final int documentOffset) {
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
                if (upper > lower) try {
                    final SearchResult search = wordTable.dictionary.searchBest(part.substring(lower, upper), documentOffset + lower);
                    if (search != null) {
                        searchCompleted(search);
                    }
                } catch (SQLException e) {
                    // TODO: We need a better handling, maybe with a widget for bug reports.
                    // For now, we let the search result to null.
                    Logging.recoverableException(EditorTextArea.class, "search", e);
                }
            }
        });
    }

    /**
     * Invoked when a search has been successfully completed.
     * The default implementation update the table view.
     */
    void searchCompleted(final SearchResult result) {
        try {
            wordTable.setContent(result.entries, result.selectedIndex);
        } catch (Throwable e) {
            Logging.recoverableException(WordTable.class, "setContent", e);
        }
    }
}
