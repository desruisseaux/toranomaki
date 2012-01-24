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
 * Kind of XML elements found in the {@code JMdict.xml} file. Each element in the Java enumeration
 * uses the same {@linkplain #name() name} than the corresponding XML element, which explain why
 * they are lower-cases. The same names are also used as column names in the SQL database.
 *
 * <p><b>Credit:</b>This enumeration javadoc is a slightly edited copy-and-paste of the EDICT
 * documentation included in the {@code JMdict.xml} file.</p>
 *
 * @author Martin Desruisseaux
 */
public enum ElementType {
    /**
     * The root element of {@code JMdict.xml} files.
     */
    JMdict(null),

    /**
     * Entries consist of Kanji elements, reading elements, general information and sense elements.
     * Each entry must have at least one reading element and one sense element. Others are optional.
     */
    entry(JMdict),

    /**
     * A unique numeric sequence number for each entry.
     */
    ent_seq(entry),

    /**
     * The Kanji element, or in its absence, the reading element, is the defining component of each
     * entry. The overwhelming majority of entries will have a single Kanji element associated with
     * a word in Japanese. Where there are multiple Kanji elements within an entry, they will be
     * orthographical variants of the same word, either using variations in okurigana, or
     * alternative and equivalent Kanji. Common "misspellings" may be included, provided they are
     * associated with appropriate information fields. Synonyms are not included; they may be
     * indicated in the cross-reference field associated with the sense element.
     *
     * @see #r_ele
     */
    k_ele(entry),

    /**
     * A word or short phrase in Japanese which is written using at least one non-kana character
     * (usually Kanji, but can be other characters). The valid characters are Kanji, kana, related
     * characters such as chouon and kurikaeshi, and in exceptional cases, letters from other
     * alphabets.
     *
     * @see #reb
     */
    keb(k_ele),

    /**
     * Coded information field related specifically to the orthography of the {@link #keb}.
     * Typically indicates some unusual aspect, such as okurigana irregularity.
     */
    ke_inf(k_ele),

    /**
     * This and the equivalent {@link #re_pri} field are provided to record information about the
     * relative priority of the entry, and consist of codes indicating the word appears in various
     * references which can be taken as an indication of the frequency with which the word is used.
     * This field is intended for use either by applications which want to concentrate on entries
     * of a particular priority, or to generate subset files. The current values in this field are:
     *
     * <ul>
     *   <li><p>{@code news1/2}: appears in the "<cite>wordfreq</cite>" file compiled by Alexandre
     *       Girardi from the <cite>Mainichi Shimbun</cite>. (See the Monash ftp archive for a copy.)
     *       Words in the first 12,000 in that file are marked "{@code news1}" and words in the second
     *       12,000 are marked "{@code news2}".</p></li>
     *   <li><p>{@code ichi1/2}: appears in the "<cite>Ichimango goi bunruishuu</cite>", Senmon Kyouiku
     *       Publishing, Tokyo, 1998. (The entries marked "{@code ichi2}" were demoted from "{@code ichi1}"
     *       because they were observed to have low frequencies in the WWW and newspapers.)</p></li>
     *   <li><p>{@code spec1/2}: a small number of words use this marker when they are detected as
     *       being common, but are not included in other lists.</p></li>
     *   <li><p>{@code gai1/2}: common loanwords, based on the "<cite>wordfreq</cite>" file.</p></li>
     *   <li><p>{@code nfxx}: this is an indicator of frequency-of-use ranking in the "<cite>wordfreq</cite>"
     *       file. "{@code xx}" is the number of the set of 500 words in which the entry can be found,
     *       with "{@code 01}" assigned to the first 500, "{@code 02}" to the second, and so on.
     *       (The entries with "{@code news1}", "{@code ichi1}", "{@code spec1}" and "{@code gai1}"
     *       values are marked with a "{@code (P)}" in the EDICT and EDICT2 files.)</p></li>
     * </ul>
     *
     * The reason both the Kanji and reading elements are tagged is because on occasions a priority
     * is only associated with a particular Kanji/reading pair.
     *
     * @see #re_pri
     * @see Priority
     */
    ke_pri(k_ele),

    /**
     * The reading element typically contains the valid readings of the word(s) in the Kanji element
     * using modern kanadzukai. Where there are multiple reading elements, they will typically be
     * alternative readings of the Kanji element. In the absence of a Kanji element, i.e. in the
     * case of a word or phrase written entirely in kana, these elements will define the entry.
     *
     * @see #k_ele
     */
    r_ele(entry),

    /**
     * This element content is restricted to kana and related characters such as chouon and
     * kurikaeshi. Kana usage will be consistent between the {@code keb} and {@code reb}
     * elements; e.g. if the {@link #keb} contains katakana, so too will the {@code reb}.
     *
     * @see #keb
     */
    reb(r_ele),

    /**
     * This element, which will usually have a null value, indicates that the {@link #reb}, while
     * associated with the {@link #keb}, cannot be regarded as a true reading of the Kanji. It is
     * typically used for words such as foreign place names, gairaigo which can be in Kanji or
     * katakana, <i>etc</i>.
     */
    re_nokanji(r_ele),

    /**
     * This element is used to indicate when the reading only applies to a subset of the {@link #keb}
     * elements in the entry. In its absence, all readings apply to all Kanji elements. The contents
     * of this element must exactly match those of one of the {@code keb} elements.
     */
    re_restr(r_ele),

    /**
     * General coded information pertaining to the specific reading. Typically it will be used to
     * indicate some unusual aspect of the reading.
     */
    re_inf(r_ele),

    /**
     * See the comment on {@link #ke_pri} above.
     *
     * @see #ke_pri
     * @see Priority
     */
    re_pri(r_ele),

    /**
     * General coded information relating to the entry as a whole.
     */
    info(entry),

    /**
     * This element holds details of linking information to entries in other electronic repositories.
     */
    links(info),

    /**
     * Coded to indicate the type of link (text, image, sound).
     */
    link_tag(links),

    /**
     * Provided a textual label for the link.
     */
    link_desc(links),

    /**
     * Contains the actual link URI.
     */
    link_uri(links),

    /**
     * Bibliographic information about the entry.
     */
    bibl(info),

    /**
     * Coded reference to an entry in an external bibliographic database.
     */
    bib_tag(bibl),

    /**
     * May be used for brief (local) descriptions.
     */
    bib_txt(bibl),

    /**
     * Holds information about the etymology of the Kanji or kana parts of the entry.
     * For gairaigo, etymological information may also be in the {@link #lsource} element.
     */
    etym(info),

    /**
     * Contains the date and other information about updates to the entry.
     * Can be used to record the source of the material.
     */
    audit(info),

    /**
     * The audit date.
     */
    upd_date(audit),

    /**
     * Other audit details.
     */
    upd_detl(audit),

    /**
     * Records the translational equivalent of the Japanese word, plus other related information.
     * Where there are several distinctly different meanings of the word, multiple sense elements
     * will be employed.
     */
    sense(entry),

    /**
     * If present, indicates that the sense is restricted to the lexeme represented by the
     * {@link #keb}.
     */
    stagk(sense),

    /**
     * If present, indicates that the sense is restricted to the lexeme represented by the
     * {@link #reb}.
     */
    stagr(sense),

    /**
     * Part-of-speech information about the entry/sense. Should use appropriate entity codes.
     * In general where there are multiple senses in an entry, the part-of-speech of an earlier
     * sense will apply to later senses unless there is a new part-of-speech indicated.
     */
    pos(sense),

    /**
     * Indicates a cross-reference to another entry with a similar or related meaning or sense.
     * The content of this element is typically a {@link #keb} or {@link #reb} element in another
     * entry. In some cases a {@code keb} will be followed by a {@code reb} and/or a sense number
     * to provide a precise target for the cross-reference. Where this happens, a JIS "centre-dot"
     * ({@code 0x2126}) is placed between the components of the cross-reference.
     */
    xref(sense),

    /**
     * Indicates another entry which is an antonym of the current entry/sense. The content of this
     * element must exactly match that of a {@link #keb} or {@link #reb} element in another entry.
     */
    ant(sense),

    /**
     * Information about the field of application of the entry/sense. When absent, general
     * application is implied. Entity coding for specific fields of application.
     */
    field(sense),

    /**
     * Used for other relevant information about the entry/sense. As with part-of-speech,
     * information will usually apply to several senses.
     */
    misc(sense),

    /**
     *
     */
    s_inf(sense),

    /**
     * Records the information about the source language(s) of a loan-word/gairaigo. If the
     * source language is other than English, the language is indicated by the {@code xml:lang}
     * attribute. The element value (if any) is the source word or phrase.
     *
     * <ul>
     *   <li><p>The {@code xml:lang} attribute defines the language(s) from which a loanword is
     *       drawn. It will be coded using the three-letter language code from the ISO 639-2
     *       standard. When absent, the value {@code "eng"} (i.e. English) is the default value.
     *       The bibliographic (B) codes are used.</p></li>
     *   <li><p>The {@code ls_type} attribute indicates whether the {@code lsource} element fully
     *       or partially describes the source word or phrase of the loanword. If absent, it will
     *       have the implied value of {@code "full"}. Otherwise it will contain {@code "part"}.</p></li>
     *   <li><p>The {@code ls_wasei} attribute indicates that the Japanese word has been constructed
     *       from words in the source language, and not from an actual phrase in that language. Most
     *       commonly used to indicate "waseieigo".</p></li>
     * </ul>
     */
    lsource(sense),

    /**
     * For words specifically associated with regional dialects in Japanese, the entity code for
     * that dialect. Example: ksb for Kansaiben.
     */
    dial(sense),

    /**
     * The {@code g_gend} attribute defines the gender of the gloss (typically a noun) in the
     * target language. When absent, the gender is either not relevant or has yet to be provided.
     */
    gloss(sense),

    /**
     * Provides for pairs of short Japanese and target-language phrases or sentences which exemplify
     * the usage of the Japanese head-word and the target-language gloss. Words in example fields
     * would typically not be indexed by a dictionary application.
     */
    example(sense);

    /**
     * The parent of this element type, or {@code null} if this element type is the root.
     */
    private final ElementType parent;

    /**
     * Creates a new element type with the given parent.
     *
     * @param parent The parent, or {@code null} if none.
     */
    private ElementType(final ElementType parent) {
        this.parent = parent;
    }

    /**
     * Returns the parent of this element type, or {@code null} if this element type is the root.
     *
     * @return The parent, or {@code null} if none.
     */
    public ElementType getParent() {
        return parent;
    }
}
