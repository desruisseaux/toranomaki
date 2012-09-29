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

import java.util.Arrays;
import fr.toranomaki.grammar.CharacterType;
import static java.lang.Character.*;


/**
 * Result of a word search. On construction, only the {@link #entries} field is valid.
 * Other fields are calculated by a call to the {@link #selectBestMatch()} method.
 *
 * @author Martin Desruisseaux
 */
public final class SearchResult {
    /**
     * The word to search.
     */
    private final String toSearch;

    /**
     * The type of characters in the word to search.
     */
    private final CharacterType characterType;

    /**
     * The entries which were examined for the search.
     */
    public final Entry[] entries;

    /**
     * Index of the selected entry in the {@link #entries} array,
     * or -1 if no match has been found.
     */
    public int selectedIndex;

    /**
     * The word which seems to be matching the search.
     */
    private String selectedWord;

    /**
     * Index of the first character of the given word in the document.
     * This information is not used by the {@code SearchResult} class,
     * but is often useful to the caller.
     */
    public int documentOffset;

    /**
     * Number of characters recognized in the string given to the search method.
     */
    public int matchLength;

    /**
     * {@code true} if every letters from the word found in the dictionary are also
     * found in the text given to the search method.
     */
    public boolean isFullMatch;

    /**
     * {@code true} if the word which has been found is a
     * {@linkplain Entry#getDerivedWords() derived word}.
     */
    public boolean isDerivedWord;

    /**
     * If this entry is highlighted in a text editor, the highlighter key as returned
     * by {@link javax.swing.text.Highlighter#addHighlight}. Null otherwise.
     */
    public Object highlighterKey;

    /**
     * Creates a new result of word search.
     *
     * @param toSearch The word to search.
     * @param entries  The search result for the word to search.
     */
    SearchResult(final String toSearch, final CharacterType type, final Entry[] entries) {
        this.toSearch = toSearch;
        this.entries  = entries;
        characterType = type;
        selectedIndex = -1;
    }

    /**
     * Searches the best entry matching the search result.
     * This method compute the values of all non-final fields.
     *
     * @return {@code true} if a match has been found, or {@code false} otherwise.
     */
    public boolean selectBestMatch() {
        Arrays.sort(entries); // Move entries with highest priority first.
        final boolean isKanji = characterType.isKanji;
        int wordLength = (selectedWord != null) ? selectedWord.length() : Integer.MAX_VALUE;
        for (int i=0; i<entries.length; i++) {
            final Entry candidate = entries[i];
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
                if (candidateFullMatch && !isFullMatch) {
                    // If the candidate fully matches the begining of the word to search, and if
                    // it was not the case of our previous best word, we will take that candidate.
                } else {
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
                        // Keep the word having highest priority.
                        if (selectedIndex >= 0 && candidate.compareTo(entries[selectedIndex]) >= 0) {
                            continue;
                        }
                    }
                }
                selectedIndex = i;
                selectedWord  = toVerify;
                matchLength   = si;
                wordLength    = candidateLength;
                isFullMatch   = candidateFullMatch;
                isDerivedWord = (variant >= 0);
            }
        }
        return selectedIndex >= 0;
    }

    /**
     * Initializes this {@code SearchResult} to the state of a previous search result.
     * This is used when we want to ensure that the new search is at least as good as
     * a previous one.
     */
    final void setInitialMatch(final SearchResult previous) {
        // Do not copy selectedIndex, because they are not index in the same array.
        selectedWord  = previous.selectedWord;
        matchLength   = previous.matchLength;
        isFullMatch   = previous.isFullMatch;
        isDerivedWord = previous.isDerivedWord;
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
        buffer.append('"').append(selectedWord).append('"');
        if (isFullMatch) {
            buffer.append(", full match");
        }
        return buffer.append(", length=").append(matchLength).append(']').toString();
    }
}
