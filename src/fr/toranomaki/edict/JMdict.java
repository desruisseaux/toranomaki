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
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
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
     * When searching for a words using the {@code LIKE} clause, maximal number of characters
     * allowed after the shortest word in order to accept an entry. This is used as an heuristic
     * filter for reducing the amount of irrelevant search results.
     *
     * @see #search(String)
     */
    private static final int MAXIMUM_EXTRA_CHARACTERS = 3;

    /**
     * The suggested minimal length of words to give to the search methods. We suggest a
     * minimal length in order to avoid returning too many results from search methods.
     *
     * @see #searchBest(String)
     */
    public static final int MINIMAL_SEARCH_LENGTH = 2;

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
     * The connection to the SQL database to be closed by the {@link #close()} method,
     * or {@code null} if this class doesn't own the connection.
     */
    private final Connection toClose;

    /**
     * The statement for reading an entry for a given {@code seq_ent} key;
     */
    private final PreparedStatement selectEntry;

    /**
     * The statement for reading the priority of a word.
     */
    private final PreparedStatement selectPriority;

    /**
     * The statement for reading the <cite>Part Of Speech</cite> (POS) information about a word.
     */
    private final PreparedStatement selectPOS;

    /**
     * The statement for reading the meaning for an entry.
     */
    private final PreparedStatement selectMeaning;

    /**
     * The statement for searching the meaning.
     */
    private final PreparedStatement searchMeaning;

    /**
     * The statement for searching a reading element.
     */
    private final PreparedStatement searchReading;

    /**
     * The statement for searching a Kanji element.
     */
    private final PreparedStatement searchKanji;

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
        exactMatch     = exact;
        toClose        = exact ? null : connection; // For now, close only if invoked from the public constructor.
        selectEntry    = prepareSelect(connection, TableOrColumn.entries,    false, ElementType.ent_seq, true, null);
        selectPriority = prepareSelect(connection, TableOrColumn.priorities, false, TableOrColumn.id,    true, null);
        selectPOS      = prepareSelect(connection, TableOrColumn.pos,        false, TableOrColumn.id,    true, null);
        selectMeaning  = prepareSelect(connection, TableOrColumn.senses,     false, ElementType.ent_seq, true, localeCodes);
        searchMeaning  = prepareSelect(connection, TableOrColumn.senses,     true,  ElementType.gloss,  exact, new String[] {"?"});
        searchReading  = prepareSelect(connection, TableOrColumn.entries,    true,  ElementType.reb,    exact, null);
        searchKanji    = prepareSelect(connection, TableOrColumn.entries,    true,  ElementType.keb,    exact, null);
    }

    /**
     * Creates a {@code SELECT} statement into the given table.
     * The statement created by this method looks like below:
     *
     * <blockquote><code>
     * SELECT keb, reb, ke_pri, re_pri FROM entries WHERE ent_seq = ?;
     * </code></blockquote>
     *
     * @param  connection  The connection to the SQL database.
     * @param  table       The table where to search.
     * @param  distinctID  {@code true} if only distinct values from the first column are wanted.
     * @param  toSearch    The column where to search a value. This column will be excluded from the result.
     * @param  exactMatch  {@code true} if the search methods should look for exact matches.
     * @param  languages   If non-null, add {@code "WHERE lang="} clauses.
     * @return The prepared statement.
     * @throws SQLException If an error occurred while preparing the statement.
     */
    private static PreparedStatement prepareSelect(final Connection connection,
            final TableOrColumn table, final boolean distinctID,
            final Enum<?> toSearch,    final boolean exactMatch,
            final String[] languages) throws SQLException
    {
        final StringBuilder sql = new StringBuilder();
        String separator = distinctID ? "SELECT DISTINCT " : "SELECT ";
        for (final Enum<?> column : table.columns) {
            if (column != toSearch) {
                sql.append(separator).append(column);
                if (distinctID) break;
                separator = ", ";
            }
        }
        sql.append(" FROM ").append(table).append(" WHERE ").append(toSearch)
                .append(exactMatch ? " = " : " LIKE ").append('?');
        if (languages != null) {
            /*
             * Restrict the search to the user language.
             */
            separator = " AND (";
            for (final String lang : languages) {
                sql.append(separator).append("lang=");
                if (!lang.equals("?")) sql.append('\'');
                sql.append(lang);
                if (!lang.equals("?")) sql.append('\'');
                separator = " OR ";
            }
            sql.append(')');
        }
        return connection.prepareStatement(sql.toString());
    }

    /**
     * Returns the entry for the given {@code ent_seq} numeric identifier.
     *
     * @param  ent_seq The key of the entry to search for.
     * @return The entry for the given key.
     * @throws SQLException If an error occurred while querying the database.
     */
    private Entry getEntry(final int ent_seq) throws SQLException {
        assert Thread.holdsLock(this);
        final Integer key = ent_seq;
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(ent_seq);
            selectEntry.setInt(1, ent_seq);
            try (final ResultSet rs = selectEntry.executeQuery()) {
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
             * Finds the meanings for the given entry. The length of the 'senses' array
             * is the number of languages (typically 2). Each entry is a comma-separated
             * list of senses in one language.
             */
            selectMeaning.setInt(1, ent_seq);
            try (final ResultSet rs = selectMeaning.executeQuery()) {
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
            entries.put(key, entry);
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
            selectPOS.setShort(1, id);
            try (final ResultSet rs = selectPOS.executeQuery()) {
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
        SearchResult result;
        final CharacterType type = CharacterType.forWord(toSearch);
        // TODO: current algorithm is inefficient, and the 2 : 4 numbers are empirical.
        String prefix = toSearch.substring(0, Math.min(toSearch.length(), type.isKanji ? 2 : 4));
        while ((result = SearchResult.search(search(prefix, type), toSearch, type.isKanji, documentOffset)) == null) {
            int length = prefix.length();
            if (length < MINIMAL_SEARCH_LENGTH) {
                break;
            }
            prefix = prefix.substring(0, length-1);
        }
        return result;
    }

    /**
     * Implementation of word search. The type given in argument must be
     * {@code CharacterType.forWord(word)}.
     */
    private synchronized Entry[] search(final String word, final CharacterType type) throws SQLException {
        final String[] searchLocales;
        final PreparedStatement stmt;
        switch (type) {
            case JOYO_KANJI: case KANJI:    stmt = searchKanji;   searchLocales = null;        break;
            case KATAKANA:   case HIRAGANA: stmt = searchReading; searchLocales = null;        break;
            case ALPHABETIC:                stmt = searchMeaning; searchLocales = localeCodes; break;
            default:                        return EMPTY_RESULT;
        }
        int minimalLength = Short.MAX_VALUE;
        stmt.setString(1, exactMatch ? word : word + '%');
        final List<Entry> entries = new ArrayList<>();
        final List<Integer> matchLengths = !exactMatch ? new ArrayList<Integer>() : null;
        for (int i=(searchLocales!=null) ? searchLocales.length : 1; --i>=0;) {
            if (searchLocales != null) {
                stmt.setString(2, searchLocales[i]);
            }
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final Entry entry = getEntry(rs.getInt(1));
                    if (matchLengths != null) {
                        final int length = entry.getShortestWord(type, word).length();
                        final int delta = length - minimalLength;
                        if (delta < 0) {
                            minimalLength = length;
                        } else if (delta > MAXIMUM_EXTRA_CHARACTERS) {
                            continue; // Word is too long, so skip it.
                        }
                        matchLengths.add(length);
                    }
                    entries.add(entry);
                }
            }
            // If we found entries in the preferred locale,
            // do not search in the fallback locale.
            if (!entries.isEmpty()) {
                break;
            }
        }
        /*
         * Before to returns the array, removes the elements which are too long
         * compared to the search criterion.
         */
        if (matchLengths != null) {
            for (int i=matchLengths.size(); --i>=0;) {
                if (matchLengths.get(i) - minimalLength > MAXIMUM_EXTRA_CHARACTERS) {
                    entries.remove(i); // Word is too long, so remove it.
                }
            }
        }
        final Entry[] array = entries.toArray(new Entry[entries.size()]);
        Arrays.sort(array); // Sort by priority.
        return array;
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
            selectPriority.setShort(1, id);
            final Enum<?>[] columns = TableOrColumn.priorities.columns;
            try (final ResultSet rs = selectPriority.executeQuery()) {
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
        searchKanji   .close();
        searchReading .close();
        searchMeaning .close();
        selectMeaning .close();
        selectPOS     .close();
        selectPriority.close();
        selectEntry   .close();
        if (toClose != null) {
            toClose.close();
        }
    }
}
