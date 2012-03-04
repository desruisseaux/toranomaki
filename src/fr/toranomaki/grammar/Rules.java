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
import java.util.logging.Level;
import fr.toranomaki.Logging;
import fr.toranomaki.edict.PartOfSpeech;


/**
 * Bases class for the definition of some basic grammatical rules. Subclasses conjugates
 * some verbs in order to get a list of alternative spelling for a given word.
 *
 * <p>This class is <strong>not</strong> thread safe. If this class needs to be used for
 * multiple thread, access most be synchronized or different instances must be used.</p>
 *
 * @author Martin Desruisseaux
 */
abstract class Rules {
    /**
     * The collection where to add the derived words. This list will be recycled every time
     * {@link #addDerivedWords(PartOfSpeech, String)} is invoked. It is caller's responsibility
     * to ensure that the list is empty before the first call for a new word.
     */
    protected final Collection<String> derivedWords;

    /**
     * Creates a new instance which will add the derived words in the given collection.
     */
    Rules(final Collection<String> derivedWords) {
        this.derivedWords = derivedWords;
    }

    /**
     * Adds to the {@linkplain #derivedWords list of derived words} every forms
     * derived from the given word.
     *
     * @param pos  The <cite>Part Of Speech</cite> of the given word.
     * @param word The word in Kanji or reading (usually hiragana) characters.
     */
    abstract void addDerivedWords(final PartOfSpeech pos, final String word);

    /**
     * Verifies that a word has the expected ending.
     * If not, logs a warning and returns {@code false}.
     */
    static boolean hasExpectedEnding(final PartOfSpeech pos, final String word, final char ending) {
        final int length = word.length();
        if (length != 0 && word.codePointBefore(length) == ending) {
            return true;
        }
        Logging.LOGGER.log(Level.WARNING, "For \"{0}\" Part Of Speech, expected a word ending "
                + "with the ''{1}'' character but got \"{2}\".", new Object[] {pos, ending, word});
        return false;
    }

    /**
     * Verifies that a word has the expected ending.
     * If not, logs a warning and returns {@code false}.
     */
    static boolean hasExpectedEnding(final PartOfSpeech pos, final String word, final String ending) {
        if (word.endsWith(ending)) {
            return true;
        }
        Logging.LOGGER.log(Level.WARNING, "For \"{0}\" Part Of Speech, expected a word ending "
                + "with the \"{1}\" suffix but got \"{2}\".", new Object[] {pos, ending, word});
        return false;
    }
}
