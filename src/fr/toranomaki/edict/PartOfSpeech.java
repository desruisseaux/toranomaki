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
package fr.toranomaki.edict;

import java.util.Locale;
import java.util.regex.Pattern;
import fr.toranomaki.grammar.GrammaticalClass;


/**
 * <cite>Part Of Speech</cite> information.
 *
 * @author Martin Desruisseaux
 */
public enum PartOfSpeech {
    /** {@code adj-f}:   Noun or verb acting prenominally.                     */ ADJECTIVE_F("prenominally", GrammaticalClass.ADJECTIVE),
    /** {@code adj-i}:   Adjective (keiyoushi).                                */ ADJECTIVE_い("keiyoushi", GrammaticalClass.ADJECTIVE),
    /** {@code adj-na}:  Adjectival nouns or quasi-adjectives (keiyodoshi).    */ ADJECTIVE_な("keiyodoshi", GrammaticalClass.ADJECTIVE),
    /** {@code adj-no}:  Nouns which may take the genitive case particle "no". */ ADJECTIVE_の("nouns no", GrammaticalClass.ADJECTIVE),
    /** {@code adj-pn}:  Pre-noun adjectival (rentaishi).                      */ ADJECTIVE_PRENOUN("rentaishi", GrammaticalClass.ADJECTIVE),
    /** {@code adj-t}:   "taru" adjective.                                     */ ADJECTIVE_TARU("taru", GrammaticalClass.ADJECTIVE),
    /** {@code adv}:     Adverb (fukushi).                                     */ ADVERB("fukushi", GrammaticalClass.ADVERB),
    /** {@code adv-to}:  Adverb taking the "to" particle.                      */ ADVERB_と("adverb to", GrammaticalClass.ADVERB),
    /** {@code aux}:     Auxiliary.                                            */ AUXILIARY("auxiliary$", GrammaticalClass.AUXILIARY),
    /** {@code aux-adj}: Auxiliary adjective.                                  */ AUXILIARY_ADJECTIVE("auxiliary adjective", GrammaticalClass.AUXILIARY),
    /** {@code aux-v}:   Auxiliary verb.                                       */ AUXILIARY_VERB("auxiliary verb", GrammaticalClass.AUXILIARY),
    /** {@code conj}:    Conjunction.                                          */ CONJUNCTION("conjunction", GrammaticalClass.CONJUNCTION),
    /** {@code exp}:     Expressions (phrases, clauses, etc.).                 */ EXPRESSION("expressions?", GrammaticalClass.EXPRESSION),
    /** {@code int}:     Interjection (kandoushi).                             */ INTERJECTION("kandoushi", GrammaticalClass.INTERJECTION),
    /** {@code num}:     Numeric.                                              */ NUMERIC("^numeric", GrammaticalClass.NUMERIC),
    /** {@code ctr}:     Counter.                                              */ COUNTER("^counter", GrammaticalClass.COUNTER),
    /** {@code pref}:    Prefix.                                               */ PREFIX("^prefix", GrammaticalClass.PREFIX),
    /** {@code suf}:     Suffix.                                               */ SUFFIX("^suffix", GrammaticalClass.SUFFIX),
    /** {@code prt}:     Particle.                                             */ PARTICLE("^particle", GrammaticalClass.PARTICLE),
    /** {@code pn}:      Pronoun.                                              */ PRONOUN("^pronoun", GrammaticalClass.PRONOUN),
    /** {@code n}:       Noun (common) (futsuumeishi).                         */ NOUN("futsuumeishi", GrammaticalClass.NOUN),
    /** {@code n-adv}:   Adverbial noun (fukushitekimeishi).                   */ NOUN_ADVERBIAL("fukushitekimeishi", GrammaticalClass.NOUN),
    /** {@code n-t}:     Noun (temporal) (jisoumeishi).                        */ NOUN_TEMPORAL("jisoumeishi", GrammaticalClass.NOUN),
    /** {@code n-pref}:  Noun, used as a prefix.                               */ NOUN_AS_PREFIX("noun prefix", GrammaticalClass.NOUN),
    /** {@code n-suf}:   Noun, used as a suffix.                               */ NOUN_AS_SUFFIX("noun suffix", GrammaticalClass.NOUN),
    /** {@code vs}:      Noun or participle which takes the aux. verb suru.    */ VERB_AS_NOUNする("noun suru", GrammaticalClass.VERB),
    /** {@code v1}:      Ichidan verb.                                         */ VERB_1("Ichidan verb$", GrammaticalClass.VERB),
    /** {@code v1z}:     Ichidan verb - zuru verb (alternative form of -jiru). */ VERB_1ずる("Ichidan zuru", GrammaticalClass.VERB),
    /** {@code v2a-s}:   Nidan verb with "u" ending (archaic).                 */ VERB_2う("Nidan u", GrammaticalClass.VERB),
    /** {@code v4h}:     Yondan verb with "hu/fu" ending (archaic).            */ VERB_4ふ("Yondan fu", GrammaticalClass.VERB),
    /** {@code v4r}:     Yondan verb with "ru" ending (archaic).               */ VERB_4る("Yondan ru", GrammaticalClass.VERB),
    /** {@code v5b}:     Godan verb with "bu" ending.                          */ VERB_5ぶ("Godan bu", GrammaticalClass.VERB),
    /** {@code v5g}:     Godan verb with "gu" ending.                          */ VERB_5ぐ("Godan gu", GrammaticalClass.VERB),
    /** {@code v5k}:     Godan verb with "ku" ending.                          */ VERB_5く("Godan ku", GrammaticalClass.VERB),
    /** {@code v5m}:     Godan verb with "mu" ending.                          */ VERB_5む("Godan mu", GrammaticalClass.VERB),
    /** {@code v5n}:     Godan verb with "nu" ending.                          */ VERB_5ぬ("Godan nu", GrammaticalClass.VERB),
    /** {@code v5r}:     Godan verb with "ru" ending.                          */ VERB_5る("Godan ru ending$", GrammaticalClass.VERB),
    /** {@code v5r-i}:   Godan verb with "ru" ending (irregular verb).         */ VERB_5る_IRREGULAR("Godan ru irregular", GrammaticalClass.VERB),
    /** {@code v5s}:     Godan verb with "su" ending.                          */ VERB_5す("Godan su", GrammaticalClass.VERB),
    /** {@code v5t}:     Godan verb with "tsu" ending.                         */ VERB_5つ("Godan tsu", GrammaticalClass.VERB),
    /** {@code v5u}:     Godan verb with "u" ending.                           */ VERB_5う("Godan u ending$", GrammaticalClass.VERB),
    /** {@code v5u-s}:   Godan verb with "u" ending (special class).           */ VERB_5う_IRREGULAR("Godan u special", GrammaticalClass.VERB),
    /** {@code v5aru}:   Godan verb - -aru special class.                      */ VERB_5ある("Godan aru", GrammaticalClass.VERB),
    /** {@code v5k-s}:   Godan verb - Iku/Yuku special class.                  */ VERB_5いく("Godan Iku", GrammaticalClass.VERB),
    /** {@code vk}:      Kuru verb - special class.                            */ VERB_くる("Kuru", GrammaticalClass.VERB),
    /** {@code vs-s}:    Suru verb - special class.                            */ VERB_する("suru special", GrammaticalClass.VERB),
    /** {@code vs-i}:    Suru verb - irregular.                                */ VERB_する_IRREGULAR("suru irregular", GrammaticalClass.VERB),
    /** {@code vn}:      Irregular nu verb.                                    */ VERB_ぬ_IRREGULAR("nu verb", GrammaticalClass.VERB),
    /** {@code vr}:      Irregular ru verb, plain form ends with -ri.          */ VERB_る_IRREGULAR("ru verb ri", GrammaticalClass.VERB),
    /** {@code vs-c}:    Su verb - precursor to the modern suru.               */ VERB_す("precursor suru", GrammaticalClass.VERB),
    /** {@code vt}:      Transitive verb.                                      */ VERB_TRANSITIVE("transitive", GrammaticalClass.VERB),
    /** {@code vi}:      Intransitive verb.                                    */ VERB_INTRANSITIVE("intransitive", GrammaticalClass.VERB);

    /**
     * A value that we can use which doesn't clash with the enum ordinal values.
     */
    static final short FIRST_AVAILABLE_ID = 100;

    /**
     * A more generic grammatical class for this enum.
     */
    public final GrammaticalClass grammaticalClass;

    /**
     * The string representation to show in graphical user interface.
     */
    private final String label;

    /**
     * Modified regular expression pattern for identifying the enum from the EDICT description.
     * For make code reading easier, this string use the whitespace for meaning {@code "\\b.+\\b"}.
     */
    private final String regex;

    /**
     * Regular expression pattern for identifying the enum from the EDICT description.
     * This is created only when first needed.
     */
    private transient Pattern pattern;

    /**
     * Creates a new enum.
     *
     * @param pattern Modified regular expression pattern for identifying the enum from the EDICT
     *                description. For make code reading easier, this string use the whitespace
     *                for meaning {@code "\\b.+\\b"}.
     */
    private PartOfSpeech(String pattern, final GrammaticalClass c) {
        regex = pattern;
        label = formatLabel(new StringBuilder(name()));
        grammaticalClass = c;
    }

    /**
     * Returns the value to use as a primary key in the database for this enum.
     */
    final short getIdentifier() {
        return (short) (ordinal() + 1);
    }

    /**
     * Reformats the given buffer in a more friendly way.
     * This method is the converse of {@link #parseLabel(String)}.
     */
    static String formatLabel(final StringBuilder buffer) {
        for (int i=buffer.length(); --i>=0;) {
            char c = buffer.charAt(i);
            switch (c) {
                case '_': c = ' '; break;
                default:  c = Character.toLowerCase(c); break;
            }
            buffer.setCharAt(i, c);
        }
        return buffer.toString();
    }

    /**
     * Returns the enum for the value stored in the database {@code "description"} column.
     * This method is the converse of {@link #formatLabel(StringBuilder)}.
     */
    static PartOfSpeech parseLabel(final String description) {
        return valueOf(description.toUpperCase(Locale.ENGLISH).replace(' ', '_'));
    }

    /**
     * Returns the <cite>Part Of Speech</cite> that match the given EDICT description.
     *
     * @param  description The description.
     * @return The <cite>Part Of Speech</cite> (never null).
     * @throws DictionaryException If no single <cite>Part Of Speech</cite> can match.
     */
    static PartOfSpeech parseEDICT(final String description) throws DictionaryException {
        PartOfSpeech pos = null;
        for (final PartOfSpeech candidate : values()) {
            Pattern pattern = candidate.pattern;
            if (pattern == null) {
                final String regex = "\\b" + candidate.regex.replace(" ", "\\b.+\\b") + "\\b";
                candidate.pattern = pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            }
            if (pattern.matcher(description).find()) {
                if (pos != null) {
                    throw new DictionaryException("Ambiguous part of speech: \"" + description +
                            "\". Both " + pos + " and " + candidate + " match.");
                }
                pos = candidate;
            }
        }
        if (pos == null) {
            throw new DictionaryException("Unrecognized part of speech: \"" + description + "\".");
        }
        return pos;
    }

    /**
     * Returns a string representation of this enum suitable for use in a graphical user interface.
     */
    @Override
    public String toString() {
        return label;
    }
}
