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
package fr.toranomaki.edict;

import static java.lang.Character.*;
import fr.toranomaki.grammar.AugmentedEntry;


/**
 * Result of a word search. Instance of this class are created by the
 * {@link DictionaryReader#searchBest(String)} method.
 *
 * @author Martin Desruisseaux
 */
public final class SearchResult {
    /**
     * The entries which were examined for the search.
     */
    public final AugmentedEntry[] entries;

    /**
     * Index of the selected entry in the {@link #entries} array.
     */
    public final int selectedIndex;

    /**
     * The word which seems to be matching the search.
     */
    public final String word;

    /**
     * Index of the first character of the given word in the document.
     * This information is not used by the {@code SearchResult} class,
     * but is often useful to the caller.
     */
    public final int documentOffset;

    /**
     * Number of characters recognized in the string given to the search method.
     */
    public final int matchLength;

    /**
     * {@code true} if every letters from the word found in the dictionary are also
     * found in the text given to the search method.
     */
    public final boolean isFullMatch;

    /**
     * {@code true} if the word which has been found is a
     * {@linkplain AugmentedEntry#getDerivedWords() derived word}.
     */
    public final boolean isDerivedWord;

    /**
     * If this entry is highlighted in a text editor, the highlighter key as returned
     * by {@link javax.swing.text.Highlighter#addHighlight}. Null otherwise.
     */
    public Object highlighterKey;

    /**
     * Creates a new result of word search.
     */
    private SearchResult(final AugmentedEntry[] entries, final int selectedIndex, final String word,
            final int documentOffset, final int matchLength, final boolean isFullMatch,
            final boolean isDerivedWord)
    {
        this.entries        = entries;
        this.selectedIndex  = selectedIndex;
        this.word           = word;
        this.documentOffset = documentOffset;
        this.matchLength    = matchLength;
        this.isFullMatch    = isFullMatch;
        this.isDerivedWord  = isDerivedWord;
    }

    /**
     * Searches the best entry matching the given text, or {@code null} if none.
     *
     * @param entries        An array of entries to consider for the search.
     * @param toSearch       The word to search.
     * @param isKanji        {@code true} for searching in Kanji elements, or {@code false}
     *                       for searching in reading elements.
     * @param documentOffset Index of the first character of the given word in the document.
     *                       This information is not used by this method. This value is simply
     *                       stored in the {@link #documentOffset} field for caller convenience.
     * @return The search result, or {@code null} if none.
     */
    static SearchResult search(final AugmentedEntry[] entries, final String toSearch, final boolean isKanji,
            final int documentOffset)
    {
        int     matchLength    = 0;
        int     wordLength     = Integer.MAX_VALUE;
        int     indexBest      = -1;
        String  word           = null;
        boolean isFullMatch    = false;
        boolean isDerivedWord  = false;
        for (int i=0; i<entries.length; i++) {
            final AugmentedEntry candidate = entries[i];
            final String[] derivedWords = candidate.getDerivedWords(isKanji);
            /*
             * First, searches among all Kanji or reading elements declared in the JMdict
             * dictionary. Next, searches among all derived elements (if any). The JMdict
             * elements have negative variant index, while derived elements have positive
             * variant index. The conversion from negative to positive index is performed
             * using the ~ (not minus) operation. Remainder: ~x = -x+1.
             */
            for (int variant=-candidate.getCount(isKanji); variant<derivedWords.length; variant++) {
                /*
                 * For each candidate words, count the number of matching characters. We will
                 * select the entry having the greatest amount of matching characters. If many
                 * entries have the same amount of matching characters, then we will select the
                 * shortest one, with JMdict words having precedence over derived words.
                 */
                final String toVerify = (variant < 0) ? candidate.getWord(isKanji, ~variant): derivedWords[variant];
                int si = 0; // toSearch index.
                int vi = 0; // toVerify index.
                while (si < toSearch.length() && vi < toVerify.length()) {
                    final int sc = toSearch.codePointAt(si); if (!isAlphabetic(sc)) {si += charCount(sc); continue;}
                    final int vc = toVerify.codePointAt(vi); if (!isAlphabetic(vc)) {vi += charCount(vc); continue;}
                    if (toUpperCase(sc) != toUpperCase(vc)) { // Be case-insensitive if the search contains latin characters.
                        break;
                    }
                    si += charCount(sc);
                    vi += charCount(vc);
                }
                final int     candidateLength    = toVerify.length();
                final boolean candidateFullMatch = (vi == candidateLength);
                if (isFullMatch && !candidateFullMatch) {
                    // If our best word is fully matched by the begining of the word to search but
                    // the current candidate word is not fully matched, discart the candidate.
                    continue;
                }
                if (isFullMatch || !candidateFullMatch) {
                    if (si < matchLength) {
                        // If there is less matching characters than our best match, reject.
                        continue;
                    }
                    if (si == matchLength) {
                        // If the candidate is longer than our best match, reject.
                        if (candidateLength > wordLength) {
                            continue;
                        }
                        // If the candidate is a derived word while our best match was a JMdict word, reject.
                        if (!isDerivedWord && variant >= 0) {
                            continue;
                        }
                    }
                }
                indexBest     = i;
                word          = toVerify;
                matchLength   = si;
                wordLength    = candidateLength;
                isFullMatch   = candidateFullMatch;
                isDerivedWord = (variant >= 0);
            }
        }
        return (indexBest >= 0) ? new SearchResult(entries, indexBest, word,
                documentOffset, matchLength, isFullMatch, isDerivedWord) : null;
    }

    /**
     * Returns a string representation of this search result for debugging purpose.
     */
    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("SearchResult[");
        if (isDerivedWord) {
            buffer.append("derived from ");
        }
        buffer.append('"').append(word).append('"');
        if (isFullMatch) {
            buffer.append(", full match");
        }
        return buffer.append(", length=").append(matchLength).append(']').toString();
    }
}
