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

import java.util.Collection;
import fr.toranomaki.edict.PartOfSpeech;


/**
 * Defines some rules relative to the grammar of adjectives.
 *
 * @author Martin Desruisseaux
 */
final class AdjectiveRules extends Rules {
    /**
     * Creates a new instance which will add the derived words in the given collection.
     */
    AdjectiveRules(final Collection<String> derivedWords) {
        super(derivedWords);
    }

    /**
     * Adds to the {@linkplain #derivedWords list of derived words} every forms
     * derived from the given word.
     *
     * @param pos  The <cite>Part Of Speech</cite> of the given word.
     * @param word The word in Kanji or reading (usually hiragana) characters.
     */
    @Override
    void addDerivedWords(final PartOfSpeech pos, final String word) {
        final Collection<String> derivedWords = this.derivedWords;
        switch (pos) {
            case ADJECTIVE_い: {
                if (hasExpectedEnding(pos, word, 'い')) {
                    final String trunk = word.substring(0, word.length() - 1);
                    derivedWords.add(word .concat("です"));
                    derivedWords.add(word .concat("でした"));
                    derivedWords.add(trunk.concat("くて"));
                    derivedWords.add(trunk.concat("くない"));
                    derivedWords.add(trunk.concat("くありません"));
                }
                break;
            }
            case ADJECTIVE_な: {
                derivedWords.add(word.concat("な"));
                derivedWords.add(word.concat("です"));
                derivedWords.add(word.concat("でした"));
                derivedWords.add(word.concat("ではありません"));
                derivedWords.add(word.concat("じゃありません"));
                break;
            }
            case ADJECTIVE_の: {
                derivedWords.add(word.concat("の"));
                break;
            }
        }
    }
}
