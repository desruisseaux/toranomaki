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
 * Defines some rules relative to the grammar of verbs.
 *
 * @author Martin Desruisseaux
 */
final class VerbRules extends Rules {
    /**
     * Creates a new instance which will add the derived words in the given collection.
     */
    VerbRules(final Collection<String> derivedWords) {
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
        if (hasExpectedEnding(pos, word)) {
            for (int i=0; i<8; i++) {
                final String derived;
                switch (i) {
                    case 0: derived = formます(pos, word, "ます"); break;
                    case 1: derived = formます(pos, word, "ました"); break;
                    case 2: derived = formます(pos, word, "ません"); break;
                    case 3: derived = formます(pos, word, "ましょう"); break;
                    case 4: derived = formます(pos, word, "たい"); break;
                    case 5: derived = formて(pos, word, 'て', 'で'); break;
                    case 6: derived = formて(pos, word, 'た', 'だ'); break;
                    case 7: derived = formない(pos, word, "ない"); break;
                    default: throw new AssertionError(i);
                }
                if (derived != null) {
                    derivedWords.add(derived);
                }
            }
        }
    }

    /**
     * Verify if the given verb has the expected ending for the given <cite>Part Of Speech</cite>.
     * If not, then this method logs a warning and returns {@code null}.
     */
    private static boolean hasExpectedEnding(final PartOfSpeech pos, final String word) {
        final char ending;
        switch (pos) {
            default: return false;
            case VERB_する_IRREGULAR: return hasExpectedEnding(pos, word, "する");
            case VERB_1: return hasExpectedEnding(pos, word, 'る');
            case VERB_5う: ending = 'う'; break;
            case VERB_5いく:
            case VERB_5く: ending = 'く'; break;
            case VERB_5ぐ: ending = 'ぐ'; break;
            case VERB_5す: ending = 'す'; break;
            case VERB_5つ: ending = 'つ'; break;
            case VERB_5ぶ: ending = 'ぶ'; break;
            case VERB_5む: ending = 'む'; break;
            case VERB_5る: ending = 'る'; break;
        }
        return hasExpectedEnding(pos, word, ending);
    }

    /**
     * For the given Japanese verb in Kanji or hiragana characters, return the verb at the
     * present time. If this method can not find such time, then {@code null} is returned.
     */
    private static String formます(final PartOfSpeech pos, final String word, final String suffix) {
        final int length = word.length();
        final char insert;
        switch (pos) {
            default: return null;
            case VERB_する_IRREGULAR: return word.substring(0, length-2) + 'し' + suffix;
            case VERB_1: return word.substring(0, length-1).concat(suffix);
            case VERB_5う:  insert = 'い'; break;
            case VERB_5いく:
            case VERB_5く:  insert = 'き'; break;
            case VERB_5ぐ:  insert = 'ぎ'; break;
            case VERB_5す:  insert = 'し'; break;
            case VERB_5つ:  insert = 'ち'; break;
            case VERB_5ぶ:  insert = 'び'; break;
            case VERB_5む:  insert = 'み'; break;
            case VERB_5る:  insert = 'り'; break;
        }
        return word.substring(0, length-1) + insert + suffix;
    }

    /**
     * For the given Japanese verb in Kanji or hiragana, return the verb at the imperative of
     * simple past time. If this method can not find such time, then {@code null} is returned.
     */
    private static String formて(final PartOfSpeech pos, final String word, char suffix, final char suffixDoux) {
        final int length = word.length();
        final char insert;
        switch (pos) {
            default: return null;
            case VERB_する_IRREGULAR: return word.substring(0, length-2) + 'し' + suffix;
            case VERB_1: return word.substring(0, length-1) + suffix;
            case VERB_5う: // Fallthrough
            case VERB_5つ: // Fallthrough
            case VERB_5る: insert = 'っ'; break;
            case VERB_5ぶ: // Fallthrough
            case VERB_5む: insert = 'ん'; suffix = suffixDoux; break;
            case VERB_5く: insert = 'い'; break;
            case VERB_5ぐ: insert = 'い'; suffix = suffixDoux; break;
            case VERB_5す: insert = 'し'; break;
        }
        return word.substring(0, length-1) + insert + suffix;
    }

    /**
     * For the given Japanese verb in Kanji or hiragana, return the verb at the familiar
     * negative form. If this method can not find such time, then {@code null} is returned.
     */
    private static String formない(final PartOfSpeech pos, final String word, final String suffix) {
        final int length = word.length();
        final char insert;
        switch (pos) {
            default: return null;
            case VERB_する_IRREGULAR: return word.substring(0, length-2) + 'し' + suffix;
            case VERB_1: return word.substring(0, length-1) + suffix;
            case VERB_5う: insert = 'わ'; break;
            case VERB_5つ: insert = 'た'; break;
            case VERB_5る: insert = 'ら'; break;
            case VERB_5む: insert = 'ま'; break;
            case VERB_5ぶ: insert = 'ば'; break;
            case VERB_5く: insert = 'か'; break;
            case VERB_5ぐ: insert = 'が'; break;
            case VERB_5す: insert = 'さ'; break;
        }
        return word.substring(0, length-1) + insert + suffix;
    }
}
