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
package fr.toranomaki.grammar;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.SearchResult;
import fr.toranomaki.edict.DictionaryReader;


/**
 * Inserts annotations in a text in the form of reading of Kanji words.
 *
 * @author Martin Desruisseaux
 */
public final class AnnotationsWriter {
    /**
     * The approximative length of the longest entry in Kanji characters.
     */
    public static final int LONGUEST_KANJI_WORD = 16;

    /**
     * Opening character for the reading.
     */
    private static final char OPEN = '〖';

    /**
     * Closing character for the reading.
     */
    private static final char CLOSE = '〗';

    /**
     * Opening character for the reading of well-known word.
     */
    private static final char OPEN_WELL_KNOWN = '【';

    /**
     * Closing character for the reading of well-known word.
     */
    private static final char CLOSE_WELL_KNOWN = '】';

    /**
     * The dictionary to use for searching words.
     */
    private final DictionaryReader dictionary;

    /**
     * Temporary buffer for preparing the fragment to insert in the text.
     */
    private final StringBuilder buffer;

    /**
     * Creates a new annotation writer using the given dictionary.
     *
     * @param dictionary The dictionary to use.
     */
    public AnnotationsWriter(final DictionaryReader dictionary) {
        this.dictionary = dictionary;
        buffer = new StringBuilder(24);
    }

    /**
     * Annotates the given text in-place.
     *
     * @param  text The text to annotate.
     */
    public synchronized void annotate(final StringBuilder text) {
        int length = text.length();
        boolean isInsideAnnotation = false;
        for (int i=0; i<length;) {
            final int c = text.codePointAt(i);
            switch (c) {
                case OPEN:  case OPEN_WELL_KNOWN:  isInsideAnnotation = true;  break;
                case CLOSE: case CLOSE_WELL_KNOWN: isInsideAnnotation = false; break;
            }
            if (!isInsideAnnotation && Character.isIdeographic(c)) {
                final SearchResult result = dictionary.searchBest(text.substring(i, Math.min(i+LONGUEST_KANJI_WORD, length)));
                if (result != null && result.isFullMatch) {
                    final Entry   entry   = result.entries[result.selectedIndex];
                    final String  kanji   = entry.getWord(true,  0);
                    final String  reading = entry.getWord(false, 0);
                    final int     end     = i + result.matchLength;
                    final boolean wk      = (kanji != null) && !kanji.equals(text.substring(i, end));
                    if (wk || reading != null) {
                        buffer.setLength(0);
                        final boolean wellKnown = entry.isWordToLearn();
                        buffer.append(wellKnown ? OPEN_WELL_KNOWN : OPEN);
                        if (wk) buffer.append(kanji).append(" ⟶ ");
                        buffer.append(reading).append(wellKnown ? CLOSE_WELL_KNOWN : CLOSE);
                        text.insert(end, buffer);
                        i = end + buffer.length();
                        length = text.length();
                        continue;
                    }
                }
            }
            i += Character.charCount(c);
        }
    }
}
