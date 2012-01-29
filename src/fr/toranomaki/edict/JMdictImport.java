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

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.io.FileInputStream;
import java.io.IOException;

import java.sql.Types;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * Imports the {@code JMDict.xml} content to the database.
 *
 * @author Martin Desruisseaux
 */
final class JMdictImport extends DefaultHandler {
    /**
     * {@code true} for executing the {@code "INSERT INTO"} SQL statement.
     * This flag is set to {@code false} only for testing purpose.
     */
    private static final boolean INSERT = true;

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
     * The {@link #partOfSpeech} values created up to date. The values are either
     * {@link PartOfSpeech} enumeration values, or instances of {@link Short} if
     * the value is actually a compound of many POS.
     */
    private final Map<String, Comparable<?>> cachePOS;

    /**
     * The patterns used in order to find the enum value of a <cite>Part Of Speech</cite>.
     */
    private final Map<PartOfSpeech, Pattern> patternPOS;

    /**
     * Next numeric identifier available for compounds <cite>Part Of Speech</cite>.
     */
    private short nextFreePosIdentifier = 100;

    /**
     * The target language of the {@linkplain #word} translation, as a 3 letters ISO code.
     * The translation itself will be stored in the database directly (no field in this class).
     *
     * @see ElementType#gloss
     */
    private String language;

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
     * All {@code ke_pri} and {@code re_pri} values found in the XML file up to date.
     */
    private final Map<Map<Priority.Type, Short>, Short> priorityCache;

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
     * The statement to use for writing in the "{@code entries}" table.
     */
    private final PreparedStatement insertEntry;

    /**
     * The statement to use for writing in the "{@code information}" table.
     */
    private final PreparedStatement insertInformation;

    /**
     * The statement to use for writing in the "{@code priorities}" table.
     */
    private final PreparedStatement insertPriority;

    /**
     * The statement to use for writing in the "{@code pos}" table.
     */
    private final PreparedStatement insertPos;

    /**
     * The statement to use for writing in the "{@code senses}" table.
     */
    private final PreparedStatement insertSense;

    /**
     * Creates a new instance which will import the {@code JMdict.xml} content using
     * the given database connection.
     *
     * @param  connection   The connection to the database where to insert the dictionary content.
     * @throws SQLException If an error occurred while preparing the SQL statements.
     */
    private JMdictImport(final Connection connection) throws SQLException {
        priorityColumns   = Priority.Type.values();
        priorities        = new EnumMap<>(Priority.Type.class);
        priorityCache     = new HashMap<>();
        partOfSpeech      = EnumSet.noneOf(PartOfSpeech.class);
        cachePOS          = new HashMap<>();
        patternPOS        = new EnumMap<>(PartOfSpeech.class);
        informations      = new HashSet<>();
        synonyms          = new HashMap<>();
        antonyms          = new HashMap<>();
        insertEntry       = prepareInsert(connection, TableOrColumn.entries);
        insertPos         = prepareInsert(connection, TableOrColumn.pos);
        insertPriority    = prepareInsert(connection, TableOrColumn.priorities);
        insertInformation = prepareInsert(connection, TableOrColumn.information);
        insertSense       = prepareInsert(connection, TableOrColumn.senses);
        for (final PartOfSpeech pos : PartOfSpeech.values()) {
            final String pattern = "\\b" + pos.pattern.replace(" ", "\\b.+\\b") + "\\b";
            patternPOS.put(pos, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
    }

    /**
     * Creates an {@code INSERT} statement into the given table.
     *
     * @param  connection The connection to the SQL database.
     * @param  table      The table where the values will be inserted.
     * @return The prepared statement.
     * @throws SQLException If an error occurred while preparing the statement.
     */
    private static PreparedStatement prepareInsert(final Connection connection,
            final TableOrColumn table) throws SQLException
    {
        final StringBuilder sql = new StringBuilder("INSERT INTO ").append(table);
        String separator = " (";
        for (final Enum<?> column : table.columns) {
            sql.append(separator).append(column);
            separator = ", ";
        }
        separator = ") VALUES (";
        for (int i=table.columns.length; --i>=0;) {
            sql.append(separator).append('?');
            separator = ", ";
        }
        return connection.prepareStatement(sql.append(')').toString());
    }

    /**
     * Executes the XML parsing and writes the information in the database.
     *
     * @param  file The path to "{@code JMdict.xml}" file to parse.
     * @throws IOException   If an I/O error occurred while reading the XML file.
     * @throws SAXException  If an error occurred while parsing the XML elements.
     * @throws SQLException  If an error occurred while writing in the database.
     * @throws DictionaryException If a logical error occurred with the XML content.
     */
    final void parse(final String file) throws IOException, SAXException, SQLException, DictionaryException {
        final XMLReader saxReader = XMLReaderFactory.createXMLReader();
        try {
            saxReader.setContentHandler(this);
            saxReader.parse(new InputSource(new FileInputStream(file)));
            complete();
        } catch (DictionaryException e) {
            // Unwraps the SQL exception for easier reading of stack trace.
            final Throwable cause = e.getCause();
            if (cause instanceof SQLException) {
                throw (SQLException) cause;
            }
            throw e;
        }
        insertEntry      .close();
        insertInformation.close();
        insertPriority   .close();
        insertPos        .close();
        insertSense      .close();
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
                language = attributes.getValue("xml:lang");
                if (language == null) {
                    language = "eng"; // Default value.
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
                        JMdict.LOGGER.log(Level.WARNING, "Priority \"{0}\" is defined twice "
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
                case pos: try {
                    if (inheritPOS) {
                        inheritPOS = false;
                        partOfSpeech.clear();
                    }
                    partOfSpeech.add(getPartOfSpeech(content));
                    break;
                } catch (SQLException e) {
                    throw new DictionaryException(e);
                }
                /*
                 * Add the meaning of a Japanese word in the target language.
                 */
                case gloss: try {
                    insertSense.setInt(1, entry.identifier);
                    if (partOfSpeech.isEmpty()) {
                        insertSense.setNull(2, Types.SMALLINT);
                    } else {
                        insertSense.setShort(2, getPartOfSpeechCode());
                    }
                    insertSense.setString(3, language);
                    insertSense.setString(4, content);
                    if (INSERT) {
                        insertSense.executeUpdate();
                    }
                    break;
                } catch (SQLException e) {
                    throw new DictionaryException(e);
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
     * Invoked when exiting a XML element. This method will write the information in the
     * database when exiting the "{@code entry}" element.
     * <p>
     * This method is public as an implementation side-effect, but should never be invoked
     * directly by anyone except the SAX parser.
     *
     * @param localName The name of the XML element.
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) {
        final ElementType type = ElementType.valueOf(localName);
        try {
            switch (type) {
                /*
                 * Add the priority to the database if not already present, then add
                 * the word to the list of words to be writen at the end of the entry.
                 */
                case k_ele:
                case r_ele: {
                    entry.add(elementType == ElementType.k_ele, word, getPriorityCode());
                    for (final String info : informations) {
                        insertInformation.setInt(1, entry.identifier);
                        insertInformation.setString(2, word);
                        insertInformation.setString(3, info);
                        if (INSERT) {
                            insertInformation.executeUpdate();
                        }
                    }
                    break;
                }
                /*
                 * Add to the database every words for this entry.
                 */
                case entry: {
                    insertEntry.setInt(1, entry.identifier);
                    final int numKanjis   = entry.getCount(true);
                    final int numReadings = entry.getCount(false);
                    final int numElements = Math.max(numKanjis, numReadings);
                    for (int i=0; i<numElements; i++) {
                        boolean isKanji = true;
                        do { // Loop will be executed exactly twice, for Kanji then for reading.
                            int p = isKanji ? 2 : 3;
                            final String w = entry.getWord(isKanji, i);
                            if (w!=null) insertEntry.setString(p, w);
                            else         insertEntry.setNull  (p, Types.VARCHAR);
                            p += 2;
                            final short c = entry.getPriority(isKanji, i);
                            if (c!=0) insertEntry.setShort(p, c);
                            else      insertEntry.setNull (p, Types.SMALLINT);
                        } while ((isKanji = !isKanji) == false);
                        if (INSERT) {
                            insertEntry.executeUpdate();
                        }
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            throw new DictionaryException(e);
        }
        elementType = type.getParent();
    }

    /**
     * Computes the priority code from the data currently stored in the {@link #priorities} map.
     * This method performs any necessary insertion in the database.
     *
     * @return The priority code, or 0 if none.
     * @throws SQLException If an error occurred while inserting the new priority values in the database.
     */
    private short getPriorityCode() throws SQLException {
        short code = 0;
        if (!priorities.isEmpty()) {
            final Short id = priorityCache.get(priorities);
            if (id != null) {
                code = id;
            } else {
                for (int i=priorityColumns.length; --i>=0;) {
                    final Priority.Type p = priorityColumns[i];
                    final Short n = priorities.get(p);
                    code += p.weight(n);
                    if (n != null) {
                        insertPriority.setShort(i+2, n);
                    } else {
                        insertPriority.setNull(i+2, Types.SMALLINT);
                    }
                }
                if (priorityCache.put(new EnumMap<>(priorities), code) != null) {
                    throw new DictionaryException("Priority code collision: " + code);
                }
                insertPriority.setShort(1, code);
                if (INSERT) {
                    insertPriority.executeUpdate();
                }
            }
        }
        return code;
    }

    /**
     * Returns a code for the <cite>Part Of Speech</cite> (POS). If there is many POS,
     * we will create a new one as the aggregation of all specified POS.
     *
     * @return The code to use, or 0 if the {@link #partOfSpeech} set is empty.
     * @throws SQLException If an error occurred while inserting the new POS value in the database.
     */
    private short getPartOfSpeechCode() throws SQLException {
        short code = 0;
        CharSequence description = null;
        for (final PartOfSpeech pos : partOfSpeech) {
            if (description == null) {
                description = pos.name();
                code = pos.getIdentifier();
            } else {
                /*
                 * Creates a StringBuilder only if we found more than 1 part of speech.
                 * Otherwise, the 'code' local variable already have the appropriate value.
                 */
                final StringBuilder buffer;
                if (description instanceof StringBuilder) {
                    buffer = (StringBuilder) description;
                } else {
                    buffer = new StringBuilder(description.toString());
                    description = buffer;
                }
                buffer.append(", ").append(pos.name());
            }
        }
        /*
         * If we had more than 1 part of speech, a StringBuilder has been created above.
         * We need to create a new entry for its content, if not already done.
         */
        if (description instanceof StringBuilder) {
            final StringBuilder buffer = (StringBuilder) description;
            for (int i=buffer.length(); --i>=0;) {
                char c = buffer.charAt(i);
                switch (c) {
                    case '_': c = ' '; break;
                    default:  c = Character.toLowerCase(c); break;
                }
                buffer.setCharAt(i, c);
            }
            description = buffer.toString();
            Comparable<?> pos = cachePOS.get(description);
            if (pos != null) {
                code = (Short) pos;
            } else {
                code = nextFreePosIdentifier++;
                cachePOS.put((String) description, code);
                insertPos.setShort(1, code);
                insertPos.setString(2, (String) description);
                if (INSERT) {
                    insertPos.executeUpdate();
                }
            }
        }
        return code;
    }

    /**
     * Parses the <cite>Part Of Speech</cite> description.
     * This method inserts new description in the database if needed.
     *
     * @param  description The <cite>Part Of Speech</cite> description.
     * @return The <cite>Part Of Speech</cite> enumeration value.
     * @throws SQLException If an error occurred while inserting the new POS value in the database.
     */
    private PartOfSpeech getPartOfSpeech(final String description) throws SQLException {
        PartOfSpeech pos = (PartOfSpeech) cachePOS.get(description);
        if (pos == null) {
            for (final Map.Entry<PartOfSpeech, Pattern> pattern : patternPOS.entrySet()) {
                if (pattern.getValue().matcher(description).find()) {
                    if (pos != null) {
                        throw new DictionaryException("Ambiguous part of speech: \"" + description +
                                "\". Both " + pos + " and " + pattern.getKey() + " match.");
                    }
                    pos = pattern.getKey();
                }
            }
            if (pos == null) {
                throw new DictionaryException("Unrecognized part of speech: \"" + description + "\".");
            }
            cachePOS.put(description, pos);
            insertPos.setShort(1, pos.getIdentifier());
            insertPos.setString(2, description);
            if (INSERT) {
                insertPos.executeUpdate();
            }
        }
        return pos;
    }

    /**
     * Invoked after the parsing of the XML file has been completed. This method
     * writes to the database any information which was deferred to the end.
     *
     * @throws SQLException If an error occurred while writing to the database.
     */
    private void complete() throws SQLException {
        final Connection connection = insertSense.getConnection();
        try (final JMdict dict = new JMdict(connection, true);
             final PreparedStatement stmt = prepareInsert(connection, TableOrColumn.xref))
        {
            boolean isAntonym = false;
            do { // Loop executed exactly twice.
                stmt.setBoolean(3, isAntonym);
                for (final Map.Entry<Integer, Set<String>> entry : (isAntonym ? antonyms : synonyms).entrySet()) {
                    stmt.setInt(1, entry.getKey());
                    for (final String word : entry.getValue()) {
                        for (final Entry xref : dict.search(word)) {
                            stmt.setInt(2, xref.identifier);
                            if (INSERT) {
                                stmt.executeUpdate();
                            }
                        }
                    }
                }
            } while ((isAntonym = !isAntonym) == true);
        }
    }

    /**
     * Run the JMdict import from the command line. This method expect a single argument,
     * which is the path to the {@code JMdict.xml} file.
     *
     * @param  args The command line arguments.
     * @throws Exception If a SQL, I/O, SAX or other exception occurred.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Expected argument: path to JMdict.xml file.");
            return;
        }
        try (final Connection connection = JMdict.getDataSource().getConnection()) {
            final JMdictImport db = new JMdictImport(connection);
            db.parse(args[0]);
        }
    }
}
