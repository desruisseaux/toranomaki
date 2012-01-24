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
import java.util.HashSet;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.logging.Level;
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
     * The entry being parsed.
     */
    private Entry entry;

    /**
     * The word to add to the entry, after we collected all its priority and information elements.
     */
    private String word;

    /**
     * The set of informations for the word in process of being parsed.
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
     * The statement to use for writing in the "{@code entry}" table.
     */
    private final PreparedStatement insertEntry;

    /**
     * The statement to use for writing in the "{@code information}" table.
     */
    private final PreparedStatement insertInformation;

    /**
     * The statement to use for writing in the "{@code priority}" table.
     */
    private final PreparedStatement insertPriority;

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
        informations      = new HashSet<>();
        insertEntry       = prepareInsert(connection, "entries", ElementType.ent_seq, ElementType.keb, ElementType.reb, ElementType.ke_pri, ElementType.re_pri);
        insertPriority    = prepareInsert(connection, "priorities", addId(priorityColumns));
        insertInformation = prepareInsert(connection, "information", ElementType.ent_seq, "element", "description");
    }

    /**
     * Adds an {@code "id"} column in from of the given column names.
     */
    private static Comparable<?>[] addId(final Comparable<?>[] columns) {
        final Comparable<?>[] copy = new Comparable<?>[columns.length + 1];
        System.arraycopy(columns, 0, copy, 1, columns.length);
        copy[0] = "id";
        return copy;
    }

    /**
     * Creates an {@code INSERT} statement into the given table and columns.
     *
     * @param  connection The connection to the SQL database.
     * @param  table      The table where the values will be inserted.
     * @param  columns    The columns where the values will be inserted.
     * @return The prepared statement.
     * @throws SQLException If an error occurred while preparing the statement.
     */
    private static PreparedStatement prepareInsert(final Connection connection,
            final String table, final Comparable<?>... columns) throws SQLException
    {
        final StringBuilder sql = new StringBuilder("INSERT INTO ").append(table);
        String separator = " (";
        for (final Comparable<?> column : columns) {
            sql.append(separator).append(column);
            separator = ", ";
        }
        separator = ") VALUES (";
        for (int i=0; i<columns.length; i++) {
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
        } catch (DictionaryException e) {
            // Unwraps the SQL exception for easier reading of stack trace.
            final Throwable cause = e.getCause();
            if (cause instanceof SQLException) {
                throw (SQLException) cause;
            }
            throw e;
        }
        insertEntry.close();
        insertInformation.close();
        insertPriority.close();
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
                break;
            }
            case k_ele:
            case r_ele: {
                word = null;
                priorities.clear();
                informations.clear();
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
            }
        }
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
}
