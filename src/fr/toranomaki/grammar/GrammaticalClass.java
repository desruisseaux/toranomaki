/*
 *    Toranomaki - Help with Japanese words using the EDICT dictionary.
 *    (C) 2012, Martin Desruisseaux
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

import java.util.ResourceBundle;


/**
 * The grammatical classes (verb, adjective...) of words in the dictionary.
 *
 * @author Martin Desruisseaux
 */
public enum GrammaticalClass {
    /**
     * Numeric
     */
    NUMERIC,

    /**
     * Counter.
     */
    COUNTER,

    /**
     * Particle.
     */
    PARTICLE,

    /**
     * Noun (common, adverbial, temporal, used as prefix or suffix).
     */
    NOUN,

    /**
     * Pronoun.
     */
    PRONOUN,

    /**
     * Conjunction.
     */
    CONJUNCTION,

    /**
     * Interjection.
     */
    INTERJECTION,

    /**
     * Auxiliary adjective or verb.
     */
    AUXILIARY,

    /**
     * Adjective (い, な, の, quasi-adjective, .
     */
    ADJECTIVE,

    /**
     * Adverb.
     */
    ADVERB,

    /**
     * Verb (Ichidan, Nidan, Yondan, Godan, irregular...).
     */
    VERB,

    /**
     * A prefix at the beginning of a sentence.
     */
    PREFIX,

    /**
     * A suffix at the end of a sentence.
     */
    SUFFIX,

    /**
     * Expressions (phrases, clauses, etc.).
     */
    EXPRESSION;

    /**
     * The localized text to display in JavaFX widgets.
     */
    private transient String label;

    /**
     * Returns the localized label to display in the widget.
     *
     * @return The localized label suitable for GUI.
     */
    @Override
    public synchronized String toString() {
        if (label == null) {
            label = ResourceBundle.getBundle("fr/toranomaki/grammar/GrammaticalClass").getString(name());
        }
        return label;
    }
}
