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


/**
 * Result of a word search. Instance of this class are created by the
 * {@link JMdict#searchBest(String)} method.
 *
 * @author Martin Desruisseaux
 */
public final class SearchResult {
    /**
     * The entry which has been found.
     */
    public final Entry entry;

    /**
     * The word which seems to be matching the search.
     */
    public final String word;

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
     * {@code true} if the word which has been found is a {@linkplain Entry#getDerivedWords()
     * derived word}.
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
    private SearchResult(final Entry entry, final String word, final int matchLength,
            final boolean isFullMatch, final boolean isDerivedWord)
    {
        this.entry         = entry;
        this.word          = word;
        this.matchLength   = matchLength;
        this.isFullMatch   = isFullMatch;
        this.isDerivedWord = isDerivedWord;
    }

    /**
     * Searches the best entry matching the given text, or {@code null} if none.
     *
     * @param entries  An array of entries to consider for the search.
     * @param toSearch The word to search.
     * @param isKanji  {@code true} for searching in Kanji elements, or {@code false}
     *                 for searching in reading elements.
     * @return The search result, or {@code null} if none.
     */
    static SearchResult search(final Entry[] entries, final String toSearch, final boolean isKanji) {
        int     matchLength    = 0;
        int     wordLength     = Integer.MAX_VALUE;
        Entry   best           = null;
        String  word           = null;
        boolean isFullMatch    = false;
        boolean isDerivedWord  = false;
        for (final Entry candidate : entries) {
            final String[] derivedWords = candidate.getDerivedWords(isKanji);
            final int numDerived = (derivedWords != null) ? derivedWords.length : 0;
            for (int variant=-candidate.getCount(isKanji); variant<numDerived; variant++) {
                /*
                 * First, searches among all Kanji or reading elements declared in the JMdict
                 * dictionary. Next, searches among all derived elements (if any). The JMdict
                 * elements have negative variant index, while derived elements have positive
                 * variant index. The conversion from negative to positive index is performed
                 * using the ~ (not minus) operation. Remainer: ~x = -x+1.
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
                /*
                 * Found the matching portion. Now check if it is
                 * any better than the best one found up to date.
                 */
                if (si < matchLength) {
                    continue;
                }
                if (si == matchLength) {
                    if (toVerify.length() > wordLength) {
                        continue;
                    }
                    if (!isDerivedWord && variant >= 0) {
                        continue;
                    }
                }
                best          = candidate;
                word          = toVerify;
                matchLength   = si;
                wordLength    = toVerify.length();
                isFullMatch   = (vi == wordLength);
                isDerivedWord = (variant >= 0);
            }
        }
        return (best != null) ? new SearchResult(best, word, matchLength, isFullMatch, isDerivedWord) : null;
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