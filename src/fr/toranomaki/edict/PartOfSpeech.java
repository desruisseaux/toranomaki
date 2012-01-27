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


/**
 * <cite>Part Of Speech</cite> information.
 *
 * @author Martin Desruisseaux
 */
public enum PartOfSpeech {
    /** {@code adj-f}:   Noun or verb acting prenominally.                     */ ADJECTIVE_F("prenominally"),
    /** {@code adj-i}:   Adjective (keiyoushi).                                */ ADJECTIVE_い("keiyoushi"),
    /** {@code adj-na}:  Adjectival nouns or quasi-adjectives (keiyodoshi).    */ ADJECTIVE_な("keiyodoshi"),
    /** {@code adj-no}:  Nouns which may take the genitive case particle "no". */ ADJECTIVE_の("nouns no"),
    /** {@code adj-pn}:  Pre-noun adjectival (rentaishi).                      */ ADJECTIVE_PRENOUN("rentaishi"),
    /** {@code adj-t}:   "taru" adjective.                                     */ ADJECTIVE_TARU("taru"),
    /** {@code adv}:     Adverb (fukushi).                                     */ ADVERB("fukushi"),
    /** {@code adv-to}:  Adverb taking the "to" particle.                      */ ADVERB_と("adverb to"),
    /** {@code aux}:     Auxiliary.                                            */ AUXILIARY("auxiliary$"),
    /** {@code aux-adj}: Auxiliary adjective.                                  */ AUXILIARY_ADJECTIVE("auxiliary adjective"),
    /** {@code aux-v}:   Auxiliary verb.                                       */ AUXILIARY_VERB("auxiliary verb"),
    /** {@code conj}:    Conjunction.                                          */ CONJUNCTION("conjunction"),
    /** {@code exp}:     Expressions (phrases, clauses, etc.).                 */ EXPRESSION("expressions?"),
    /** {@code int}:     Interjection (kandoushi).                             */ INTERJECTION("kandoushi"),
    /** {@code num}:     Numeric.                                              */ NUMERIC("^numeric"),
    /** {@code ctr}:     Counter.                                              */ COUNTER("^counter"),
    /** {@code pref}:    Prefix.                                               */ PREFIX("^prefix"),
    /** {@code suf}:     Suffix.                                               */ SUFFIX("^suffix"),
    /** {@code prt}:     Particle.                                             */ PARTICLE("^particle"),
    /** {@code pn}:      Pronoun.                                              */ PRONOUN("^pronoun"),
    /** {@code n}:       Noun (common) (futsuumeishi).                         */ NOUN("futsuumeishi"),
    /** {@code n-adv}:   Adverbial noun (fukushitekimeishi).                   */ NOUN_ADVERBIAL("fukushitekimeishi"),
    /** {@code n-t}:     Noun (temporal) (jisoumeishi).                        */ NOUN_TEMPORAL("jisoumeishi"),
    /** {@code n-pref}:  Noun, used as a prefix.                               */ NOUN_AS_PREFIX("noun prefix"),
    /** {@code n-suf}:   Noun, used as a suffix.                               */ NOUN_AS_SUFFIX("noun suffix"),
    /** {@code vs}:      Noun or participle which takes the aux. verb suru.    */ VERB_AS_NOUNする("noun suru"),
    /** {@code v1}:      Ichidan verb.                                         */ VERB_1("Ichidan verb$"),
    /** {@code v1z}:     Ichidan verb - zuru verb (alternative form of -jiru). */ VERB_1ずる("Ichidan zuru"),
    /** {@code v2a-s}:   Nidan verb with "u" ending (archaic).                 */ VERB_2う("Nidan u"),
    /** {@code v4h}:     Yondan verb with "hu/fu" ending (archaic).            */ VERB_4ふ("Yondan fu"),
    /** {@code v4r}:     Yondan verb with "ru" ending (archaic).               */ VERB_4る("Yondan ru"),
    /** {@code v5b}:     Godan verb with "bu" ending.                          */ VERB_5ぶ("Godan bu"),
    /** {@code v5g}:     Godan verb with "gu" ending.                          */ VERB_5ぐ("Godan gu"),
    /** {@code v5k}:     Godan verb with "ku" ending.                          */ VERB_5く("Godan ku"),
    /** {@code v5m}:     Godan verb with "mu" ending.                          */ VERB_5む("Godan mu"),
    /** {@code v5n}:     Godan verb with "nu" ending.                          */ VERB_5ぬ("Godan nu"),
    /** {@code v5r}:     Godan verb with "ru" ending.                          */ VERB_5る("Godan ru ending$"),
    /** {@code v5r-i}:   Godan verb with "ru" ending (irregular verb).         */ VERB_5る_IRREGULAR("Godan ru irregular"),
    /** {@code v5s}:     Godan verb with "su" ending.                          */ VERB_5す("Godan su"),
    /** {@code v5t}:     Godan verb with "tsu" ending.                         */ VERB_5つ("Godan tsu"),
    /** {@code v5u}:     Godan verb with "u" ending.                           */ VERB_5う("Godan u ending$"),
    /** {@code v5u-s}:   Godan verb with "u" ending (special class).           */ VERB_5う_IRREGULAR("Godan u special"),
    /** {@code v5aru}:   Godan verb - -aru special class.                      */ VERB_5ある("Godan aru"),
    /** {@code v5k-s}:   Godan verb - Iku/Yuku special class.                  */ VERB_5いく("Godan Iku"),
    /** {@code vk}:      Kuru verb - special class.                            */ VERB_くる("Kuru"),
    /** {@code vs-s}:    Suru verb - special class.                            */ VERB_する("suru special"),
    /** {@code vs-i}:    Suru verb - irregular.                                */ VERB_する_IRREGULAR("suru irregular"),
    /** {@code vn}:      Irregular nu verb.                                    */ VERB_ぬ_IRREGULAR("nu verb"),
    /** {@code vr}:      Irregular ru verb, plain form ends with -ri.          */ VERB_る_IRREGULAR("ru verb ri"),
    /** {@code vs-c}:    Su verb - precursor to the modern suru.               */ VERB_す("precursor suru"),
    /** {@code vt}:      Transitive verb.                                      */ VERB_TRANSITIVE("transitive"),
    /** {@code vi}:      Intransitive verb.                                    */ VERB_INTRANSITIVE("intransitive");

    /**
     * Modified regular expression pattern for identifying the enum from the EDICT description.
     * For make code reading easier, this string use the whitespace for meaning {@code "\\b.+\\b"}.
     */
    final String pattern;

    /**
     * Creates a new enum.
     */
    private PartOfSpeech(final String pattern) {
        this.pattern = pattern;
    }

    /**
     * Returns the value to use as a primary key in the database for this enum.
     */
    final short getIdentifier() {
        return (short) (ordinal() + 1);
    }
}
