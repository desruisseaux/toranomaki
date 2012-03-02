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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

import fr.toranomaki.grammar.CharacterType;


/**
 * Connects to the EDICT {@code JMdict} dictionary database.
 *
 * @author Martin Desruisseaux
 */
public final class JMdict implements AutoCloseable {
    /**
     * The logger for reporting warnings.
     */
    public static final Logger LOGGER = Logger.getLogger("fr.toranomaki.edict");

    /**
     * The array to returns from the search method when no matching entry has been found.
     */
    private static final Entry[] EMPTY_RESULT = new Entry[0];

    /**
     * Maximal number of rows to consider before and after the word to search. The number of
     * search results should typically be reasonable (e.g. 20 entries). Nevertheless we put
     * an arbitrary limit as a safety.
     */
    private static final int MAXIMUM_ROWS = 100;

    /**
     * A cache of most recently used entries. The cache capacity is arbitrary, but we are
     * better to use a value not greater than a power of 2 time the load factor (0.75).
     */
    @SuppressWarnings("serial")
    private final Map<Integer,Entry> entries = new LinkedHashMap<Integer,Entry>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 12000; // Arbitrary cache capacity (see javadoc).
        }
    };

    /**
     * A cache for the priorities information about a word.
     *
     * @see ElementType#ke_pri
     * @see ElementType#re_pri
     */
    private final Map<Short, Set<Priority>> priorities = new HashMap<>();

    /**
     * A cache for the <cite>Part Of Speech</cite> (POS) information about a word.
     *
     * @see ElementType#pos
     */
    private final Map<Short, Set<PartOfSpeech>> partOfSpeech = new HashMap<>();

    /**
     * The locales for which to search meanings, in <strong>reverse</strong> of preference order.
     * We use reverse order because the English is the most extensively used language in the EDICT
     * dictionary, so it is worth to put it first in our data structure. But it still only the
     * fallback language for non-English users.
     * <p>
     * The default values are {@linkplain Locale#ENGLISH English} followed by the
     * {@linkplain Locale#getDefault() system default}, if different then English.
     */
    private final Locale[] locales;

    /**
     * The 3-letters language codes for the {@link #locales}, in the same order.
     *
     * @see Locale#getISO3Language()
     */
    private final String[] localeCodes;

    /**
     * {@code true} if this dictionary is looking for exact matches.
     */
    private final boolean exactMatch;

    /**
     * {@code true} if the {@linkplain #connection} should be closed by the {@link #close()} method.
     * If {@code false}, only the {@linkplain #statements} will be closed.
     */
    private final boolean closeConnection;

    /**
     * The connection to the SQL database.
     */
    private final Connection connection;

    /*
     * The following static constants are index in the 'statements' array. Some values are
     * derived arithmetically, and no value shall overlap. This class contains a 'switch'
     * statement using all constant values, including the value derived arithmetically.
     * We rely on 'javac' for producing a compilation error if some values overlap.
     */

    /**
     * Index of the statement for reading an entry for a given {@code seq_ent} key;
     */
    private static final int SELECT_ENTRY = 0;

    /**
     * Index of the statement for reading the priority of a word.
     */
    private static final int SELECT_PRIORITY = 1;

    /**
     * Index of the statement for reading the <cite>Part Of Speech</cite> (POS) information about a word.
     */
    private static final int SELECT_POS = 2;

    /**
     * Index of the statement for reading the meaning for an entry.
     */
    private static final int SELECT_MEANING = 3;

    /**
     * Index of the statement for searching the meaning.
     * The statement at the index+1 search in descending order.
     */
    private static final int SEARCH_MEANING = 4;

    /**
     * Index of the statement for searching a reading element.
     * The statement at the index+1 search in descending order.
     */
    private static final int SEARCH_READING = 6;

    /**
     * The statement for searching a Kanji element.
     * The statement at the index+1 search in descending order.
     */
    private static final int SEARCH_KANJI = 8;

    /**
     * The prepared statements, created when first needed.
     * The length must be equals to the last of the above-cited index + 2.
     */
    private final PreparedStatement[] statements = new PreparedStatement[10];

    /**
     * A temporary set used by {@link #search(String)} only. Its content is cleared after
     * every search operations.
     */
    private final Set<Integer> matchingEntryID = new LinkedHashSet<>();

    /**
     * Creates a new connection to the dictionary.
     *
     * @param  datasource The database source.
     * @throws SQLException If an error occurred while preparing the statements.
     */
    public JMdict(final DataSource datasource) throws SQLException {
        this(datasource.getConnection(), false);
    }

    /**
     * Creates a new connection to the dictionary.
     *
     * @param  connection The connection to the database.
     * @param  exact {@code true} if the search methods should look for exact matches.
     * @throws SQLException If an error occurred while preparing the statements.
     */
    JMdict(final Connection connection, final boolean exact) throws SQLException {
        exactMatch      = exact;
        closeConnection = !exact; // For now, close only if invoked from the public constructor.
        this.connection = connection;
        final Locale locale = Locale.getDefault();
        if (locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            locales = new Locale[] {locale};
        } else {
            locales = new Locale[] {Locale.ENGLISH, locale}; // Reverse of preference order.
        }
        localeCodes = new String[locales.length];
        for (int i=0; i<localeCodes.length; i++) {
            localeCodes[i] = locales[i].getISO3Language();
        }
    }

    /**
     * Returns the prepared statement at the given index. The index must be one of the
     * {@code SELECT_*} or {@code SEARCH_*} constant.
     * <p>
     * This method creates the statement only when first needed, then stores it for reuse.
     * The statement created by this method looks like below:
     *
     * <blockquote><code>
     * SELECT ent_seq, keb, reb, ke_pri, re_pri FROM entries WHERE ent_seq = ?;
     * </code></blockquote>
     */
    private PreparedStatement getStatement(final int index) throws SQLException {
        assert Thread.holdsLock(this);
        PreparedStatement stmt = statements[index];
        if (stmt == null) {
            final TableOrColumn table;  // The table where to search.
            final Enum<?> toSearch;     // The column where to search a value.
            final boolean exact;        // Whatever to perform an exact search.
            String[] lang = null;       // If non-null, add "WHERE lang=" clauses.
            switch (index) {            // We rely on this switch for ensuring non-overlapping constants!
                case SELECT_ENTRY:     table=TableOrColumn.entries;    toSearch=ElementType.ent_seq; exact=true;  break;
                case SELECT_PRIORITY:  table=TableOrColumn.priorities; toSearch=TableOrColumn.id;    exact=true;  break;
                case SELECT_POS:       table=TableOrColumn.pos;        toSearch=TableOrColumn.id;    exact=true;  break;
                case SELECT_MEANING:   table=TableOrColumn.senses;     toSearch=ElementType.ent_seq; exact=true;  lang=localeCodes; break;
                case SEARCH_MEANING:   // Search in (ascending|descending) order.
                case SEARCH_MEANING|1: table=TableOrColumn.senses;     toSearch=ElementType.gloss;   exact=false; lang=localeCodes; break;
                case SEARCH_READING:   // Search in (ascending|descending) order.
                case SEARCH_READING|1: table=TableOrColumn.entries;    toSearch=ElementType.reb;     exact=false; break;
                case SEARCH_KANJI:     // Search in (ascending|descending) order.
                case SEARCH_KANJI|1:   table=TableOrColumn.entries;    toSearch=ElementType.keb;     exact=false; break;
                default: throw new AssertionError(index);
            }
            // Control the comparator to use: -1 for "<", +1 for ">=" or 0 for "=".
            final int order = (exact | exactMatch) ? 0 : (index & 1) != 0 ? -1 : +1;
            final StringBuilder sql = new StringBuilder();
            /*
             * Create the "SELECT columns" part of the SQL statement.   We do not include
             * the column to search if an exact match were required, since the content of
             * that column is already known.  Additionally we will include only the first
             * column (which is the identifier) and the search column if a non-exact match
             * were required, since we will need to perform a separate search for entries
             * by identifier.
             */
            if (exact) {
                String separator = "SELECT ";
                for (final Enum<?> column : table.columns) {
                    if (column != toSearch) {
                        sql.append(separator).append(column);
                        separator = ", ";
                    }
                }
            } else {
                sql.append("SELECT ").append(table.columns[0]).append(", ").append(toSearch);
            }
            /*
             * Appends the "FROM table WHERE condition" part of the SQL statement.
             * The conditions will restrict the search to the user preferred languages
             * if the 'lang' array is non-null.
             */
            sql.append(" FROM ").append(table).append(" WHERE ").append(toSearch).append(operator(order)).append('?');
            if (order != 0) {
                sql.append(" AND ").append(toSearch).append(operator(-order)).append('?');
            }
            if (lang != null) {
                String separator = " AND (";
                for (final String lg : lang) {
                    sql.append(separator).append("lang=").append('\'').append(lg).append('\'');
                    separator = " OR ";
                }
                sql.append(')');
            }
            /*
             * Complete the last part of the SQL statement if needed, put a limit
             * on the number of rows as a safety and cache the new prepared statement.
             */
            if (order != 0) {
                sql.append(" ORDER BY ").append(toSearch).append(order >= 0 ? " ASC" : " DESC");
            }
            stmt = connection.prepareStatement(sql.toString());
            stmt.setMaxRows(MAXIMUM_ROWS);
            statements[index] = stmt;
        }
        return stmt;
    }

    /**
     * Returns the SQL comparison order for the given order code.
     */
    private static String operator(final int order) {
        return (order == 0) ? " = " : (order >= 0 ? " >= " : " < ");
    }

    /**
     * Returns the entry for the given {@code ent_seq} numeric identifier.
     *
     * @param  id The identifier of the entry to search for.
     * @return The entry for the given key.
     * @throws SQLException If an error occurred while querying the database.
     */
    private Entry getEntry(final Integer id) throws SQLException {
        assert Thread.holdsLock(this);
        final int ent_seq = id;
        Entry entry = entries.get(id);
        if (entry == null) {
            entry = new Entry(ent_seq);
            PreparedStatement stmt = getStatement(SELECT_ENTRY);
            stmt.setInt(1, ent_seq);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    boolean isKanjiResult = true;
                    do { // This loop is executed twice: once for Kanji and once for reading element.
                        final String found = rs.getString(isKanjiResult ? 1 : 2);
                        if (!rs.wasNull()) {
                            short priority = rs.getShort(isKanjiResult ? 3 : 4);
                            if (rs.wasNull()) {
                                priority = 0;
                            }
                            entry.add(isKanjiResult, found, priority);
                        }
                    } while ((isKanjiResult = !isKanjiResult) == false);
                }
            }
            /*
             * Find the meanings for the given entry. The length of the 'senses' array
             * is the number of languages (typically 2). Each entry is a comma-separated
             * list of senses in one language.
             */
            stmt = getStatement(SELECT_MEANING);
            stmt.setInt(1, ent_seq);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final Set<PartOfSpeech> pos = getPartOfSpeech(rs.getShort(1));
                    final String lang  = rs.getString(2);
                    final String gloss = rs.getString(3);
                    for (int i=0; i<localeCodes.length; i++) {
                        if (lang.equals(localeCodes[i])) {
                            entry.addSense(new Sense(locales[i], gloss, pos));
                            // Should have exactly one occurrence, but
                            // let continue as a matter of principle.
                        }
                    }
                }
            }
            entry.addSenseSummary(locales);
            entries.put(id, entry);
        }
        return entry;
    }

    /**
     * Returns the <cite>Part Of Speech</cite> information for the given {@code pos} primary key.
     *
     * @param  id The key of the {@code pos} entry to search for.
     * @return The {@code pos} entry for the given key.
     * @throws SQLException If an error occurred while querying the database.
     */
    private Set<PartOfSpeech> getPartOfSpeech(final Short id) throws SQLException {
        assert Thread.holdsLock(this);
        Set<PartOfSpeech> pos = partOfSpeech.get(id);
        if (pos == null) {
            pos = EnumSet.noneOf(PartOfSpeech.class);
            final boolean isCompound = (id >= PartOfSpeech.FIRST_AVAILABLE_ID);
            final PreparedStatement stmt = getStatement(SELECT_POS);
            stmt.setShort(1, id);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String description = rs.getString(1).trim();
                    if (!isCompound) {
                        pos.add(PartOfSpeech.parseEDICT(description));
                    } else for (final String token : description.split(",")) {
                        pos.add(PartOfSpeech.parseLabel(token.trim()));
                    }
                }
            }
            partOfSpeech.put(id, pos);
        }
        return pos;
    }

    /**
     * Returns all entries matching the given criterion.
     *
     * @param  word The Kanji or reading word to search.
     * @return All entries for the given words.
     * @throws SQLException If an error occurred while querying the database.
     */
    public Entry[] search(final String word) throws SQLException {
        if (word == null || word.isEmpty()) {
            return EMPTY_RESULT;
        }
        return search(word, CharacterType.forWord(word));
    }

    /**
     * Searches the best entry matching the given text, or {@code null} if none.
     *
     * @param toSearch       The word to search.
     * @param documentOffset Index of the first character of the given word in the document.
     *                       This information is not used by this method. This value is simply
     *                       stored in the {@link #documentOffset} field for caller convenience.
     * @return The search result, or {@code null} if none.
     * @throws SQLException If an error occurred while querying the database.
     */
    public SearchResult searchBest(final String toSearch, final int documentOffset) throws SQLException {
        if (toSearch == null || toSearch.isEmpty()) {
            return null;
        }
        final CharacterType type = CharacterType.forWord(toSearch);
        return SearchResult.search(search(toSearch, type), toSearch, type.isKanji, documentOffset);
    }

    /**
     * Implementation of word search. The type given in argument must be
     * {@code CharacterType.forWord(word)}.
     */
    private synchronized Entry[] search(final String word, final CharacterType type) throws SQLException {
        matchingEntryID.clear(); // Safety in case a previous call failed before completion.
        final int index;
        switch (type) {
            case JOYO_KANJI: case KANJI:    index = SEARCH_KANJI;   break;
            case KATAKANA:   case HIRAGANA: index = SEARCH_READING; break;
            case ALPHABETIC:                index = SEARCH_MEANING; break;
            default: return EMPTY_RESULT;
        }
        final PreparedStatement stmt1 = getStatement(index); // Search in ascending order.
        stmt1.setString(1, word);
        if (!exactMatch) {
            // Create a single character to be used as an upper bound.
            stmt1.setString(2, String.valueOf(Character.toChars(word.codePointAt(0) + 1)));
        }
        PreparedStatement stmt2 = null; // To be created only if needed.
        /*
         * Search matching words in ascending order first. If the very first record begin
         * with all of the 'word' character, there is no need to execute the search in
         * descending order. Otherwise, get the first record of the search in descending
         * order before to continue the search in ascending order, in order to known how
         * many common characters to look for.
         *
         * Example: If we search for "ABCD" in a dictionary containing only "ABCC" and "ABDD",
         * then the "entry >= 'ABCD'" condition while returns "ABDD". But the previous entry,
         * "ABCC" was a better march, so we need to check for it.
         */
        try (final ResultSet rs1 = stmt1.executeQuery()) {
            int commonLength = collectFirstID(rs1, word);
            if (commonLength < word.length()) {
                /*
                 * If we enter in this block, we have determined that the search in ascending
                 * order will not be suffisient (it would have been suffisient if the begining
                 * of our first entry contained all the characters of the word to search). So
                 * we need to run a search in descending order.
                 */
                if (stmt2 == null) {
                    stmt2 = getStatement(index | 1);
                    stmt2.setString(1, word);
                    if (!exactMatch) {
                        // We will want at least the number of characters matching so far.
                        stmt2.setString(2, word.substring(0, Math.max(commonLength, Character.charCount(word.codePointAt(0)))));
                    }
                }
                try (final ResultSet rs2 = stmt2.executeQuery()) {
                    final int n = collectFirstID(rs2, word);
                    if (n != 0 && n >= commonLength) {
                        if (commonLength != n) {
                            commonLength = n;
                            rs1.close();
                            matchingEntryID.clear(); // Remove the entry collected by the ascending search.
                        }
                        collectEntryID(rs2, word, commonLength);
                    }
                }
            }
            if (!rs1.isClosed()) {
                collectEntryID(rs1, word, commonLength);
            }
        }
        /*
         * At this point, we have the identifier of all entries.
         * Now get the actual Entry instances.
         */
        int i = 0;
        final Entry[] array = new Entry[matchingEntryID.size()];
        for (final Integer id : matchingEntryID) {
            array[i++] = getEntry(id);
        }
        matchingEntryID.clear();
        assert i == array.length;
        Arrays.sort(array); // Sort by priority.
        return array;
    }

    /**
     * Fetches only the first entry from the given result set and stores its identifier
     * in the {@link #matchingEntryID} set. If no record is found, this method closes the
     * result set and returns 0.
     *
     * @param  rs   The result set from which to perform the search.
     * @param  word The word to search.
     * @return The length of the common prefix, or 0 if none.
     * @throws SQLException If an error occurred while querying the database.
     */
    private int collectFirstID(final ResultSet rs, final String word) throws SQLException {
        if (rs.next()) {
            final int commonLength = commonPrefixLength(word, rs.getString(2));
            if (commonLength != 0) {
                matchingEntryID.add(rs.getInt(1));
                return commonLength;
            }
        }
        rs.close();
        return 0;
    }

    /**
     * Fetches all remaining entries from the given result set and stores their identifiers
     * in the {@link #matchingEntryID} set.
     *
     * @param  rs   The result set from which to perform the search.
     * @param  word The word to search.
     * @throws SQLException If an error occurred while querying the database.
     */
    private void collectEntryID(final ResultSet rs,
            final String word, final int prefixLength) throws SQLException
    {
        while (rs.next()) {
            final int n = commonPrefixLength(word, rs.getString(2));
            if (n < prefixLength) break;
            matchingEntryID.add(rs.getInt(1));
        }
    }

    /**
     * Returns the length of the prefix which is common to the two given string.
     * The comparison is case-sensitive.
     */
    private static int commonPrefixLength(final String s1, final String s2) {
        final int length = Math.min(s1.length(), s2.length());
        int i = 0;
        while (i < length) {
            // No need to use the codePointAt API, since we are looking for exact match.
            if (s1.charAt(i) != s2.charAt(i)) {
                break;
            }
            i++;
        }
        return i;
    }

    /**
     * Returns the priority information for the given primary key. The values given to this
     * method are typically values returned by {@link Entry#getPriority(boolean, int)}.
     *
     * @param  id The primary key of the priorities to search for.
     * @return The priorities for the given primary key.
     * @throws SQLException If an error occurred while querying the database.
     *
     * @see Entry#getPriority(boolean, int)
     */
    public synchronized Set<Priority> getPriority(final Short id) throws SQLException {
        Set<Priority> result = priorities.get(id);
        if (result == null) {
            final PreparedStatement stmt = getStatement(SELECT_PRIORITY);
            stmt.setShort(1, id);
            final Enum<?>[] columns = TableOrColumn.priorities.columns;
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // columns[0] is the id, which we skip.
                    for (int i=1; i<columns.length; i++) {
                        final short n = rs.getShort(i);
                        if (rs.wasNull()) {
                            continue;
                        }
                        final Priority.Type type = (Priority.Type) columns[i];
                        final Short singletonCode = (short) type.weight(n);
                        Set<Priority> singleton = priorities.get(singletonCode);
                        /*
                         * Before to create a new Priority instance, try to recycle an existing one
                         * from a singleton. If there is no existing singleton Priority instance, we
                         * will unconditionally add one in order to allow subsequent call to recycle
                         * the cached instance.
                         */
                        final Priority p;
                        if (singleton != null) {
                            final Iterator<Priority> it = singleton.iterator();
                            p = it.next();
                            assert !it.hasNext() : singletonCode;
                        } else {
                            p = new Priority(type, n);
                            singleton = Collections.singleton(p);
                            priorities.put(singletonCode, singleton);
                        }
                        assert p.type == type && p.rank == n : p;
                        /*
                         * Adds the Priority to the Set. A HashSet instance will need to be
                         * created only if there is at least 2 priority instances.
                         */
                        if (result == null) {
                            result = singleton;
                        } else {
                            if (result.size() == 1) {
                                result = new HashSet<>(result);
                            }
                            result.add(p);
                        }
                    }
                }
            }
            if (result == null) {
                result = Collections.emptySet();
            } else if (result.size() != 1) {
                result = Collections.unmodifiableSet(result);
            }
            priorities.put(id, result);
        }
        return result;
    }

    /**
     * Closes the prepared statements used by this object.
     *
     * @throws SQLException If an error occurred while closing the statements.
     */
    @Override
    public synchronized void close() throws SQLException {
        entries.clear();
        for (int i=0; i<statements.length; i++) {
            final PreparedStatement stmt = statements[i];
            if (stmt != null) {
                stmt.close();
                statements[i] = null;
            }
        }
        if (closeConnection) {
            connection.close();
        }
    }
}
