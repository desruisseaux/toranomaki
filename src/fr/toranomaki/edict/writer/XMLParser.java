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
package fr.toranomaki.edict.writer;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.PartOfSpeech;
import fr.toranomaki.edict.Priority;
import fr.toranomaki.edict.DictionaryException;

import static fr.toranomaki.edict.BinaryData.getDirectory;


/**
 * Converts the {@code JMDict.xml} file to the binary formats.
 * The result is stored in the public {@code fooList} fields.
 *
 * <p>Usage:</p>
 * <blockquote><pre>
 * final JMdictImport db = new JMdictImport();
 * try (InputStream in = getDefaultStream()) {
 *     db.parse(in);
 * }
 * // Use the content of public fields.
 * </pre></blockquote>
 *
 * @author Martin Desruisseaux
 */
final class XMLParser extends DefaultHandler {
    /**
     * The logger for reporting warnings.
     */
    private static final Logger LOGGER = Logger.getLogger("fr.toranomaki.edict");

    /**
     * The XML element which is in process of being parsed. This field is modified every time a XML
     * element is started or ended. This information is used by {@link #characters(char[], int, int)}
     * in order to determine what to do with the XML element value.
     */
    private ElementType elementType;

    /**
     * The entry being parsed. A new instance is created every time a new {@code <entry>}
     * element start. This object contains an arbitrary amount of Kanji and reading elements.
     *
     * @see ElementType#entry
     */
    private Entry entry;

    /**
     * The next Kanji or reading element to add to the {@linkplain #entry}. The addition
     * will happen only after we collected all priority and information elements.
     *
     * @see ElementType#keb
     * @see ElementType#reb
     */
    private String word;

    /**
     * The <cite>Part Of Speech</cite> (POS) information about the word.
     *
     * @see ElementType#pos
     */
    private final Set<PartOfSpeech> partOfSpeech;

    /**
     * {@code true} if no {@code <pos>} declarations has been explicitely defined in the current
     * {@code <sense>} element. In such case, the next {@code <sense>} elements will inherit the
     * POS from the previous {@code <sense>} element.
     */
    private boolean inheritPOS;

    /**
     * The {@link #partOfSpeech} values parsed up to date.
     */
    private final Map<String, PartOfSpeech> parsedPOS;

    /**
     * Cached {@link #partOfSpeech} instances.
     */
    private final Map<Set<PartOfSpeech>, Set<PartOfSpeech>> cachedPOS;

    /**
     * The target language of the {@linkplain #word} translation.
     *
     * @see ElementType#gloss
     */
    private Locale language;

    /**
     * The languages to keep. Other languages will be discarded.
     */
    private final String[] retainedLanguages;

    /**
     * The set of informations for the word in process of being parsed.
     *
     * @see ElementType#info
     */
    private final Set<String> informations;

    /**
     * The set of priorities for the word in process of being parsed.
     */
    private final Map<Priority.Type, Short> priorities;

    /**
     * All {@code ke_pri} and {@code re_pri} values found in the XML file.
     * The keys are priority value for each priority type. The values are
     * the numeric code given to that combination.
     */
    private final Map<Map<Priority.Type, Short>, Short> priorityMap;

    /**
     * Every priorities to be written in the database.
     */
    private final Priority.Type[] priorityColumns;

    /**
     * The synonyms and antonyms for an entry. Those values will be written only after we
     * finished to write every entries in the database, in order to resolve cross-references.
     * <p>
     * The keys are the {@link ElementType#ent_seq} value for which the synonym or antonym is
     * declared. The values are the defining element (Kanji or reading) which will need to be
     * resolved.
     */
    private final Map<Integer, Set<String>> synonyms, antonyms;

    /**
     * The list of entries parsed from the XML file.
     */
    public final List<Entry> entryList;

    /**
     * The elements included in the {@link #informationList}.
     */
    static final class Info {
        final Entry entry;
        final String word;
        final String info;

        Info(final Entry entry, final String word, final String info) {
            this.entry = entry;
            this.word  = word;
            this.info  = info;
        }
    }

    /**
     * The list of informations parsed from the XML file.
     */
    public final List<Info> informationList;

    /**
     * The list of priorities parsed from the XML file.
     */
    public final List<String> priorityList;

    /**
     * The list of <cite>Part Of Speech</cite> parsed from the XML file.
     */
    public final List<PartOfSpeech> posList;

    /**
     * Creates a new instance which will import the {@code JMdict.xml} content.
     *
     * @param retainedLanguages The languages to keep. Other languages will be discarded.
     */
    public XMLParser(final Locale[] locales) {
        priorityColumns   = Priority.Type.values();
        priorities        = new EnumMap<>(Priority.Type.class);
        priorityMap       = new HashMap<>();
        partOfSpeech      = EnumSet.noneOf(PartOfSpeech.class);
        parsedPOS         = new HashMap<>();
        cachedPOS         = new HashMap<>();
        informations      = new HashSet<>();
        synonyms          = new HashMap<>();
        antonyms          = new HashMap<>();
        entryList         = new ArrayList<>(130000);
        posList           = new ArrayList<>(128);
        priorityList      = new ArrayList<>(128);
        informationList   = new ArrayList<>(128);
        retainedLanguages = new String[locales.length];
        for (int i=0; i<locales.length; i++) {
            retainedLanguages[i] = locales[i].getLanguage();
        }
    }

    /**
     * Returns an input stream for the default {@link JMdict.xml} file to parse.
     * This stream can be given to the {@link #parse(InputStream)} method.
     *
     * @return The input stream of the default {@link JMdict.xml} file to parse.
     * @throws IOException If the input stream can not be opened.
     */
    public static InputStream getDefaultStream() throws IOException {
        final InputStream in;
        final File file = getDirectory().resolve("JMdict.xml").toFile();
        if (file.isFile()) {
            in = new FileInputStream(file);
        } else {
            in = XMLParser.class.getResourceAsStream("JMdict.xml");
            if (in == null) {
                throw new FileNotFoundException("JMdict.xml");
            }
        }
        return in;
    }

    /**
     * Parses the XML file and stores the entries. This method can be invoked
     * for many XML files to parse, but there is usually only one.
     *
     * @param  in The input stream for the "{@code JMdict.xml}" file to parse.
     * @throws IOException   If an I/O error occurred while reading the XML file.
     * @throws SAXException  If an error occurred while parsing the XML elements.
     * @throws DictionaryException If a logical error occurred with the XML content.
     */
    public void parse(final InputStream in) throws IOException, SAXException, DictionaryException {
        final XMLReader saxReader = XMLReaderFactory.createXMLReader();
        saxReader.setContentHandler(this);
        saxReader.parse(new InputSource(in));
    }

    /**
     * Returns all <cite>Part of speech</cite> sets which have been found while parsing
     * the XML file. There is few distinct sets (about 400), and each set is expected to
     * contain at most 8 elements.
     */
    public Set<Set<PartOfSpeech>> getPartOfSpeechSets() {
        return cachedPOS.keySet();
    }

    /**
     * Invoked when entering in a new XML element. This method determines the {@link ElementType}
     * and ensures that it is consistent with the allowed type.
     * <p>
     * This method is public as an implementation side-effect, but should never be invoked
     * directly by anyone except the SAX parser.
     *
     * @param localName The name of the XML element.
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName,
            final Attributes attributes)
    {
        final ElementType type = ElementType.valueOf(localName);
        switch (type) {
            case entry: {
                entry = null;
                partOfSpeech.clear();
                break;
            }
            case k_ele:
            case r_ele: {
                word = null;
                priorities.clear();
                informations.clear();
                break;
            }
            case sense: {
                inheritPOS = true;
                break;
            }
            case gloss: {
                final String lang = attributes.getValue("xml:lang");
                if (lang == null) {
                    language = Locale.ENGLISH; // Default value.
                } else {
                    switch (lang) {
                        case "eng": language = Locale.ENGLISH; break;
                        case "ger": language = Locale.GERMAN;  break;
                        case "fre": language = Locale.FRENCH;  break;
                        case "rus": language = Sense .RUSSIAN; break;
                        default: throw new DictionaryException("Unknown language: " + lang);
                    }
                }
                break;
            }
        }
        final ElementType current = elementType;
        final ElementType parent  = type.getParent();
        if (parent != current && (current == null || parent != current.getParent())) {
            throw new DictionaryException("Unexpected location for <" + localName + "> element. " +
                    "Expected a child of <" + parent + "> but was a child or a sibling of <" + current + ">.");
        }
        elementType = type;
    }

    /**
     * Invoked for processing the content of a XML element. This processing will depend
     * on the current value of {@link #elementType}.
     * <p>
     * This method is public as an implementation side-effect, but should never be invoked
     * directly by anyone except the SAX parser.
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) {
        final String content = String.valueOf(ch, start, length).trim();
        if (!content.isEmpty()) {
            switch (elementType) {
                /*
                 * Create a new entry for the given sequence number.
                 * This must be the first element in every entries.
                 */
                case ent_seq: {
                    if (entry != null) {
                        throw new DictionaryException("Only one <ent_seq> is allowed inside an <entry> element.");
                    }
                    entry = new Entry(Integer.parseInt(content));
                    break;
                }
                /*
                 * Remember the word. We will add the word to the entry only after the element end,
                 * because we need to know all priorities and information associated to that word.
                 */
                case keb:
                case reb: {
                    if (word != null && !content.equals(word)) {
                        throw new DictionaryException("Duplicated <" + elementType + "> element.");
                    }
                    word = content;
                    break;
                }
                /*
                 * Adds an information associated to the current entry.
                 */
                case ke_inf:
                case re_inf: {
                    informations.add(content);
                    break;
                }
                /*
                 * Add the priority code to the set of priorities for this element.
                 * Will be inserted into the database together with the word later.
                 */
                case ke_pri:
                case re_pri: {
                    final Priority p = new Priority(content);
                    final Short rank = p.rank;
                    final Short old  = priorities.put(p.type, rank);
                    if (old != null) {
                        LOGGER.log(Level.WARNING, "Priority \"{0}\" is defined twice "
                                + "for word \"{1}\" with values {2} and {3}.",
                                new Object[] {p.type, word, old, rank});
                        if (old < rank) {
                            // Keep the highest priority.
                            priorities.put(p.type, old);
                        }
                    }
                    break;
                }
                /*
                 * Set the "part of speech" code. If we were inheriting the POS values from
                 * the previous <sense> entry, clear the collection since the new declarations
                 * replace the inherited ones.
                 */
                case pos: {
                    if (inheritPOS) {
                        inheritPOS = false;
                        partOfSpeech.clear();
                    }
                    partOfSpeech.add(getPartOfSpeech(content));
                    break;
                }
                /*
                 * Add the meaning of a Japanese word in the target language.
                 */
                case gloss: {
                    final String lang = language.getLanguage();
                    for (final String retained : retainedLanguages) {
                        if (lang.equals(retained)) {
                            Set<PartOfSpeech> pos = cachedPOS.get(partOfSpeech);
                            if (pos == null) {
                                pos = EnumSet.copyOf(partOfSpeech);
                                cachedPOS.put(pos, pos);
                            }
                            entry.addSense(new Sense(language, content, pos));
                            break;
                        }
                    }
                    break;
                }
                /*
                 * Add a synonym or antonym.
                 */
                case xref: addTo(synonyms, entry.identifier, content); break;
                case ant:  addTo(antonyms, entry.identifier, content); break;
            }
        }
    }

    /**
     * Adds the given string into the given map. This is an helper method for writing into
     * the {@link #synonyms} and {@link #antonyms} maps. Those information will be written
     * to the database when {@link #complete()} will be invoked.
     */
    private static void addTo(final Map<Integer, Set<String>> map, final Integer id, final String word) {
        Set<String> set = map.get(id);
        if (set == null) {
            set = new HashSet<>();
            map.put(id, set);
        }
        set.add(word);
    }

    /**
     * Invoked when exiting a XML element. This method will stores the information related
     * to the "{@code entry}" element.
     * <p>
     * This method is public as an implementation side-effect, but should never be invoked
     * directly by anyone except the SAX parser.
     *
     * @param localName The name of the XML element.
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) {
        final ElementType type = ElementType.valueOf(localName);
        switch (type) {
            /*
             * Add the priority to the database if not already present, then add
             * the word to the list of words to be writen at the end of the entry.
             */
            case k_ele:
            case r_ele: {
                entry.add(elementType == ElementType.k_ele, word, getPriorityCode());
                for (final String info : informations) {
                    informationList.add(new Info(entry, word, info));
                }
                break;
            }
            /*
                * Add to the database every words for this entry.
                */
            case entry: {
                entryList.add(entry);
                break;
            }
        }
        elementType = type.getParent();
    }

    /**
     * Computes the priority code from the data currently stored in the {@link #priorities} map.
     *
     * @return The priority code, or 0 if none.
     */
    private short getPriorityCode() {
        short code = 0;
        if (!priorities.isEmpty()) {
            final Short id = priorityMap.get(priorities);
            if (id != null) {
                code = id;
            } else {
                for (int i=priorityColumns.length; --i>=0;) {
                    final Priority.Type p = priorityColumns[i];
                    final Short n = priorities.get(p); // May be null.
                    code += p.rankToCode(n);
                    assert Objects.equals(n, p.codeToRank(code)) : code;
                }
                if (priorityMap.put(new EnumMap<>(priorities), code) != null) {
                    throw new DictionaryException("Priority code collision: " + code);
                }
            }
        }
        return code;
    }

    /**
     * Parses the <cite>Part Of Speech</cite> description.
     *
     * @param  description The <cite>Part Of Speech</cite> description.
     * @return The <cite>Part Of Speech</cite> enumeration value.
     */
    private PartOfSpeech getPartOfSpeech(final String description) {
        PartOfSpeech pos = parsedPOS.get(description);
        if (pos == null) {
            pos = PartOfSpeech.parseEDICT(description);
            parsedPOS.put(description, pos);
            posList.add(pos);
        }
        return pos;
    }
}
