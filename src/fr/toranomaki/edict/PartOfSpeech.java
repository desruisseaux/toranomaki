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

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import fr.toranomaki.grammar.GrammaticalClass;


/**
 * <cite>Part Of Speech</cite> information.
 *
 * @author Martin Desruisseaux
 */
public enum PartOfSpeech {
    /** {@code adj-f}:   Noun or verb acting prenominally.                      */ ADJECTIVE_F("prenominally", GrammaticalClass.ADJECTIVE),
    /** {@code adj-i}:   Adjective (keiyoushi).                                 */ ADJECTIVE_い("keiyoushi", GrammaticalClass.ADJECTIVE),
    /** {@code adj-na}:  Adjectival nouns or quasi-adjectives (keiyodoshi).     */ ADJECTIVE_な("keiyodoshi", GrammaticalClass.ADJECTIVE),
    /** {@code adj-no}:  Nouns which may take the genitive case particle "no".  */ ADJECTIVE_の("nouns no", GrammaticalClass.ADJECTIVE),
    /** {@code adj-pn}:  Pre-noun adjectival (rentaishi).                       */ ADJECTIVE_PRENOUN("rentaishi", GrammaticalClass.ADJECTIVE),
    /** {@code adj-t}:   "taru" adjective.                                      */ ADJECTIVE_たる("taru", GrammaticalClass.ADJECTIVE),
    /** {@code adj-kari}:"kari" adjective (archaic)                             */ ADJECTIVE_かり("kari", GrammaticalClass.ADJECTIVE),
    /** {@code adj-ku}:  "ku" adjective (archaic)                                 */ ADJECTIVE_く("ku adjective", GrammaticalClass.ADJECTIVE),
    /** {@code adj-shiku}:"shiku" adjective (archaic)                           */ ADJECTIVE_しく("shiku adjective", GrammaticalClass.ADJECTIVE),
    /** {@code adj-nari}:archaic/formal form of na-adjective                    */ ADJECTIVE_なり("formal na-adjective", GrammaticalClass.ADJECTIVE),
    /** {@code adv}:     Adverb (fukushi).                                      */ ADVERB("fukushi", GrammaticalClass.ADVERB),
    /** {@code adv-to}:  Adverb taking the "to" particle.                       */ ADVERB_と("adverb to", GrammaticalClass.ADVERB),
    /** {@code aux}:     Auxiliary.                                             */ AUXILIARY("auxiliary$", GrammaticalClass.AUXILIARY),
    /** {@code aux-adj}: Auxiliary adjective.                                   */ AUXILIARY_ADJECTIVE("auxiliary adjective", GrammaticalClass.AUXILIARY),
    /** {@code aux-v}:   Auxiliary verb.                                        */ AUXILIARY_VERB("auxiliary verb", GrammaticalClass.AUXILIARY),
    /** {@code conj}:    Conjunction.                                           */ CONJUNCTION("conjunction", GrammaticalClass.CONJUNCTION),
    /** {@code exp}:     Expressions (phrases, clauses, etc.).                  */ EXPRESSION("expressions?", GrammaticalClass.EXPRESSION),
    /** {@code int}:     Interjection (kandoushi).                              */ INTERJECTION("kandoushi", GrammaticalClass.INTERJECTION),
    /** {@code num}:     Numeric.                                               */ NUMERIC("^numeric", GrammaticalClass.NUMERIC),
    /** {@code ctr}:     Counter.                                               */ COUNTER("^counter", GrammaticalClass.COUNTER),
    /** {@code pref}:    Prefix.                                                */ PREFIX("^prefix", GrammaticalClass.PREFIX),
    /** {@code suf}:     Suffix.                                                */ SUFFIX("^suffix", GrammaticalClass.SUFFIX),
    /** {@code prt}:     Particle.                                              */ PARTICLE("^particle", GrammaticalClass.PARTICLE),
    /** {@code pn}:      Pronoun.                                               */ PRONOUN("^pronoun", GrammaticalClass.PRONOUN),
    /** {@code n}:       Noun (common) (futsuumeishi).                          */ NOUN("futsuumeishi", GrammaticalClass.NOUN),
    /** {@code n-adv}:   Adverbial noun (fukushitekimeishi).                    */ NOUN_ADVERBIAL("fukushitekimeishi", GrammaticalClass.NOUN),
    /** {@code n-t}:     Noun (temporal) (jisoumeishi).                         */ NOUN_TEMPORAL("jisoumeishi", GrammaticalClass.NOUN),
    /** {@code n-pref}:  Noun, used as a prefix.                                */ NOUN_AS_PREFIX("noun prefix", GrammaticalClass.NOUN),
    /** {@code n-suf}:   Noun, used as a suffix.                                */ NOUN_AS_SUFFIX("noun suffix", GrammaticalClass.NOUN),
    /** {@code vs}:      Noun or participle which takes the aux. verb suru.     */ VERB_AS_NOUNする("noun suru", GrammaticalClass.VERB),
    /** {@code v1}:      Ichidan verb.                                          */ VERB_1("Ichidan verb$", GrammaticalClass.VERB),
    /** {@code v1z}:     Ichidan verb - zuru verb (alternative form of -jiru).  */ VERB_1ずる("Ichidan zuru", GrammaticalClass.VERB),
    /** {@code v2a-s}:   Nidan verb with "u" ending (archaic).                  */ VERB_2う("Nidan u", GrammaticalClass.VERB),
    /** {@code v2k-k}:   Nidan verb (upper class) with "ku" ending (archaic)    */ VERB_2く("Nidan upper ku", GrammaticalClass.VERB),
    /** {@code v2g-k}:   Nidan verb (upper class) with "gu" ending (archaic)    */ VERB_2ぐ("Nidan upper gu", GrammaticalClass.VERB),
    /** {@code v2t-k}:   Nidan verb (upper class) with "tsu" ending (archaic)   */ VERB_2つ("Nidan upper tsu", GrammaticalClass.VERB),
    /** {@code v2d-k}:   Nidan verb (upper class) with "dzu" ending (archaic)   */ VERB_2ず("Nidan upper dzu", GrammaticalClass.VERB),
    /** {@code v2h-k}:   Nidan verb (upper class) with "hu/fu" ending (archaic) */ VERB_2ふ("Nidan upper hu", GrammaticalClass.VERB),
    /** {@code v2b-k}:   Nidan verb (upper class) with "bu" ending (archaic)    */ VERB_2ぶ("Nidan upper bu", GrammaticalClass.VERB),
    /** {@code v2m-k}:   Nidan verb (upper class) with "mu" ending (archaic)    */ VERB_2む("Nidan upper mu", GrammaticalClass.VERB),
    /** {@code v2y-k}:   Nidan verb (upper class) with "yu" ending (archaic)    */ VERB_2ゔ("Nidan upper vu", GrammaticalClass.VERB),
    /** {@code v2r-k}:   Nidan verb (upper class) with "ru" ending (archaic)    */ VERB_2る("Nidan upper ru", GrammaticalClass.VERB),
    /** {@code v2k-s}:   Nidan verb (lower class) with "ku" ending (archaic)    */ VERB_2くL("Nidan lower ku", GrammaticalClass.VERB),
    /** {@code v2g-s}:   Nidan verb (lower class) with "gu" ending (archaic)    */ VERB_2ぐL("Nidan lower gu", GrammaticalClass.VERB),
    /** {@code v2s-s}:   Nidan verb (lower class) with "su" ending (archaic)    */ VERB_2L("Nidan lower su", GrammaticalClass.VERB),
    /** {@code v2z-s}:   Nidan verb (lower class) with "zu" ending (archaic)    */ VERB_2ずL("Nidan lower zu", GrammaticalClass.VERB),
    /** {@code v2t-s}:   Nidan verb (lower class) with "tsu" ending (archaic)   */ VERB_2つL("Nidan lower tsu", GrammaticalClass.VERB),
    /** {@code v2d-s}:   Nidan verb (lower class) with "dzu" ending (archaic)   */ VERB_2ZL("Nidan lower dzu", GrammaticalClass.VERB),
    /** {@code v2n-s}:   Nidan verb (lower class) with "nu" ending (archaic)    */ VERB_2ぬL("Nidan lower nu", GrammaticalClass.VERB),
    /** {@code v2h-s}:   Nidan verb (lower class) with "hu/fu" ending (archaic) */ VERB_2ふL("Nidan lower hu", GrammaticalClass.VERB),
    /** {@code v2b-s}:   Nidan verb (lower class) with "bu" ending (archaic)    */ VERB_2ぶL("Nidan lower bu", GrammaticalClass.VERB),
    /** {@code v2m-s}:   Nidan verb (lower class) with "mu" ending (archaic)    */ VERB_2むL("Nidan lower mu", GrammaticalClass.VERB),
    /** {@code v2y-s}:   Nidan verb (lower class) with "yu" ending (archaic)    */ VERB_2ゔL("Nidan lower yu", GrammaticalClass.VERB),
    /** {@code v2r-s}:   Nidan verb (lower class) with "ru" ending (archaic)    */ VERB_2るL("Nidan lower ru", GrammaticalClass.VERB),
    /** {@code v2w-s}:   Nidan verb (lower class) with "u" ending and "we" ...  */ VERB_2("Nidan u we", GrammaticalClass.VERB),
    /** {@code v4h}:     Yodan verb with "hu/fu" ending (archaic).              */ VERB_4ふ("Yodan fu", GrammaticalClass.VERB),
    /** {@code v4r}:     Yodan verb with "ru" ending (archaic).                 */ VERB_4る("Yodan ru", GrammaticalClass.VERB),
    /** {@code v4k}:     Yodan verb with `ku' ending (archaic).                 */ VERB_4く("Yodan ku", GrammaticalClass.VERB),
    /** {@code v4g}:     Yodan verb with `gu' ending (archaic).                 */ VERB_4ぐ("Yodan gu", GrammaticalClass.VERB),
    /** {@code v4s}:     Yodan verb with `su' ending (archaic).                 */ VERB_4す("Yodan su", GrammaticalClass.VERB),
    /** {@code v4t}:     Yodan verb with `tsu' ending (archaic).                */ VERB_4つ("Yodan tsu", GrammaticalClass.VERB),
    /** {@code v4n}:     Yodan verb with `nu' ending (archaic).                 */ VERB_4ぬ("Yodan nu", GrammaticalClass.VERB),
    /** {@code v4b}:     Yodan verb with `bu' ending (archaic).                 */ VERB_4ぶ("Yodan bu", GrammaticalClass.VERB),
    /** {@code v4m}:     Yodan verb with `mu' ending (archaic).                 */ VERB_4む("Yodan mu", GrammaticalClass.VERB),
    /** {@code v5b}:     Godan verb with "bu" ending.                           */ VERB_5ぶ("Godan bu", GrammaticalClass.VERB),
    /** {@code v5g}:     Godan verb with "gu" ending.                           */ VERB_5ぐ("Godan gu", GrammaticalClass.VERB),
    /** {@code v5k}:     Godan verb with "ku" ending.                           */ VERB_5く("Godan ku", GrammaticalClass.VERB),
    /** {@code v5m}:     Godan verb with "mu" ending.                           */ VERB_5む("Godan mu", GrammaticalClass.VERB),
    /** {@code v5n}:     Godan verb with "nu" ending.                           */ VERB_5ぬ("Godan nu", GrammaticalClass.VERB),
    /** {@code v5r}:     Godan verb with "ru" ending.                           */ VERB_5る("Godan ru ending$", GrammaticalClass.VERB),
    /** {@code v5r-i}:   Godan verb with "ru" ending (irregular verb).          */ VERB_5る_IRREGULAR("Godan ru irregular", GrammaticalClass.VERB),
    /** {@code v5s}:     Godan verb with "su" ending.                           */ VERB_5す("Godan su", GrammaticalClass.VERB),
    /** {@code v5t}:     Godan verb with "tsu" ending.                          */ VERB_5つ("Godan tsu", GrammaticalClass.VERB),
    /** {@code v5u}:     Godan verb with "u" ending.                            */ VERB_5う("Godan u ending$", GrammaticalClass.VERB),
    /** {@code v5u-s}:   Godan verb with "u" ending (special class).            */ VERB_5う_IRREGULAR("Godan u special", GrammaticalClass.VERB),
    /** {@code v5aru}:   Godan verb - -aru special class.                       */ VERB_5ある("Godan aru", GrammaticalClass.VERB),
    /** {@code v5k-s}:   Godan verb - Iku/Yuku special class.                   */ VERB_5いく("Godan Iku", GrammaticalClass.VERB),
    /** {@code vk}:      Kuru verb - special class.                             */ VERB_くる("Kuru", GrammaticalClass.VERB),
    /** {@code vs-s}:    Suru verb - special class.                             */ VERB_する("suru special", GrammaticalClass.VERB),
    /** {@code vs-i}:    Suru verb - irregular.                                 */ VERB_する_IRREGULAR("suru irregular", GrammaticalClass.VERB),
    /** {@code vn}:      Irregular nu verb.                                     */ VERB_ぬ_IRREGULAR("nu verb", GrammaticalClass.VERB),
    /** {@code vr}:      Irregular ru verb, plain form ends with -ri.           */ VERB_る_IRREGULAR("ru verb ri", GrammaticalClass.VERB),
    /** {@code vs-c}:    Su verb - precursor to the modern suru.                */ VERB_す("precursor suru", GrammaticalClass.VERB),
    /** {@code vt}:      Transitive verb.                                       */ VERB_TRANSITIVE("transitive", GrammaticalClass.VERB),
    /** {@code vi}:      Intransitive verb.                                     */ VERB_INTRANSITIVE("intransitive", GrammaticalClass.VERB);

    /**
     * A value that we can use which doesn't clash with the enum ordinal values.
     */
    static final short FIRST_AVAILABLE_ID = 100;

    /**
     * A cache of descriptions generated for different collection of <cite>Part Of Speech</cite>.
     */
    private static final Map<Set<PartOfSpeech>, String> DESCRIPTIONS = new HashMap<>();

    /**
     * A more generic grammatical class for this enum.
     */
    public final GrammaticalClass grammaticalClass;

    /**
     * The string representation to show in graphical user interface.
     */
    private final String label;

    /**
     * The description from the EDICT database. This is initialized to {@code null},
     * then modified to a more accurate value when this information become available.
     */
    private String description;

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
     * Reformats the given buffer in a more friendly way.
     * This method is the converse of {@link #parseLabel(String)}.
     */
    private static String formatLabel(final StringBuilder buffer) {
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
     * Returns the <cite>Part Of Speech</cite> that match the given EDICT description.
     *
     * @param  description The description.
     * @return The <cite>Part Of Speech</cite> (never null).
     * @throws DictionaryException If no single <cite>Part Of Speech</cite> can match.
     */
    public static PartOfSpeech parseEDICT(final String description) throws DictionaryException {
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
                if (pos.description == null) {
                    pos.description = description;
                }
            }
        }
        if (pos == null) {
            throw new DictionaryException("Unrecognized part of speech: \"" + description + "\".");
        }
        return pos;
    }

    /**
     * Returns a comma-separated list of all <cite>part of speech</cite> descriptions.
     *
     * @param pos The collection of part of speech. Must be immutable.
     * @return A comma-separated list, or {@code null} if the given collection is empty.
     */
    public static synchronized String getDescriptions(final Set<PartOfSpeech> pos) {
        String value = DESCRIPTIONS.get(pos);
        if (value == null) {
            CharSequence partOfSpeech = null;
            for (final PartOfSpeech item : pos) {
                final String description = item.getDescription();
                if (partOfSpeech == null) {
                    partOfSpeech = description;
                } else {
                    final StringBuilder buffer;
                    if (partOfSpeech instanceof StringBuilder) {
                        buffer = (StringBuilder) partOfSpeech;
                    } else {
                        buffer = new StringBuilder(partOfSpeech);
                        partOfSpeech = buffer;
                    }
                    buffer.append(", ").append(description);
                }
            }
            if (partOfSpeech != null) {
                value = partOfSpeech.toString();
                DESCRIPTIONS.put(pos, value);
            }
        }
        return value;
    }

    /**
     * Returns a long description of this enum.
     *
     * @return A long description of this enum.
     */
    public String getDescription() {
        final String description = this.description;
        return (description != null) ? description : label;
    }

    /**
     * Returns a string representation of this enum suitable for use in a graphical user interface.
     *
     * @return The text to show in the GUI.
     */
    @Override
    public String toString() {
        return label;
    }
}
