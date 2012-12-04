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

import java.util.Set;
import java.util.Collection;
import java.util.LinkedHashSet;
import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.PartOfSpeech;


/**
 * Applies a few basic grammatical rules on a given entry, in order to get a list
 * of alternative spelling of each words.
 *
 * @author Martin Desruisseaux
 */
public final class Grammar {
    /**
     * An empty array of strings, for the common cases where no derived words could be computed.
     */
    private static final String[] EMPTY = new String[0];

    /**
     * The default instance.
     */
    public static final Grammar DEFAULT = new Grammar();

    /**
     * The collection where to add the derived words.
     */
    private final Collection<String> derivedWords;

    /**
     * Default set of rules to apply on {@link Entry} instances.
     */
    private final Rules adjectives, verbs;

    /**
     * Creates a new instance.
     */
    private Grammar() {
        derivedWords = new LinkedHashSet<>();
        adjectives   = new AdjectiveRules(derivedWords);
        verbs        = new VerbRules(derivedWords);
    }

    /**
     * Computes derived words for the given entry. This method is used only for the
     * implementation of {@link Entry#getDerivedWords(boolean)} and should not be used
     * directly.
     *
     * @param  entry The entry for which to compute derived words.
     * @param  isKanji {@code true} for computing Kanji derived words,
     *         or {@code false} for computing derived words of reading elements.
     * @return The derived words.
     *
     * @see Entry#getDerivedWords(boolean)
     */
    public String[] getDerivedWords(final Entry entry, final boolean isKanji) {
        final String[] result;
        final Set<PartOfSpeech> ps = entry.getSenseSummmary().partOfSpeech;
        synchronized (derivedWords) {
            try {
                final int count = entry.getCount(isKanji);
                for (int i=0; i<count; i++) {
                    final String word = entry.getWord(isKanji, i);
                    for (final PartOfSpeech pos : ps) {
                        final Rules rules;
                        switch (pos.grammaticalClass) {
                            case ADJECTIVE: rules = adjectives; break;
                            case VERB:      rules = verbs;      break;
                            default:        continue;
                        }
                        rules.addDerivedWords(pos, word);
                    }
                }
                result = derivedWords.isEmpty() ? EMPTY : derivedWords.toArray(new String[derivedWords.size()]);
            } finally {
                derivedWords.clear();
            }
        }
        return result;
    }

    /**
     * Attempts to return the radical of the given word. The current implementation just returns
     * the heading Kanji characters, skipping trailing hiragana characters. This is a very naive
     * implementation which doesn't work in many cases, but it can be used as an approximation.
     * If no leading Kanji character is found, then this method returns the given string unchanged.
     *
     * @param  word The word from which to get the radical, if possible.
     * @return A string that <em>may</em> reasonably be the radial (caller needs to verify with
     *         the dictionary) or the given word unchanged.
     */
    public static String radical(String word) {
        final int length = word.length();
        int upper = 0;
        while (upper < length) {
            final int c = word.codePointAt(upper);
            if (!Character.isIdeographic(c)) break;
            upper += Character.charCount(c);
        }
        if (upper != 0) {
            word = word.substring(0, upper);
        }
        return word;
    }
}
