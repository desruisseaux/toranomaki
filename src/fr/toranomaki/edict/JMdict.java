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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
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
     * A cache of most recently used entries. The cache capacity is arbitrary, but we are
     * better to use a value not greater than a power of 2 time the load factor (0.75).
     */
    @SuppressWarnings("serial")
    private final Map<Integer,Entry> entries = new LinkedHashMap<Integer,Entry>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 6100; // Arbitrary cache capacity (see javadoc).
        }
    };

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
     * Selects the meaning for an entry.
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
     * Returns the data source to use for the connection to the SQL database.
     *
     * @todo Connect to a local PostgreSQL database for now. Final version will connect to Derby.
     */
    static DataSource getDataSource() {
        final org.postgresql.ds.PGSimpleDataSource datasource = new org.postgresql.ds.PGSimpleDataSource();
        datasource.setServerName("localhost");
        datasource.setDatabaseName("Toranomaki");
        datasource.setUser("postgres");
        return datasource;
    }

    /**
     * Creates a new connection to the dictionary.
     *
     * @throws SQLException If an error occurred while preparing the statements.
     */
    public JMdict() throws SQLException {
        this(getDataSource().getConnection(), false);
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
        exactMatch    = exact;
        toClose       = exact ? null : connection; // For now, close only if invoked from the public constructor.
        selectEntry   = prepareSelect(connection, TableOrColumn.entries, false, ElementType.ent_seq, true, null);
        selectMeaning = prepareSelect(connection, TableOrColumn.senses,  false, ElementType.ent_seq, true, localeCodes);
        searchMeaning = prepareSelect(connection, TableOrColumn.senses,  true,  ElementType.gloss,  exact, localeCodes);
        searchReading = prepareSelect(connection, TableOrColumn.entries, true,  ElementType.reb,    exact, null);
        searchKanji   = prepareSelect(connection, TableOrColumn.entries, true,  ElementType.keb,    exact, null);
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
            final TableOrColumn table,  final boolean distinctID,
            final ElementType toSearch, final boolean exactMatch,
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
                sql.append(separator).append("lang='").append(lang).append('\'');
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
            entry.setSenses(locales, getMeanings(ent_seq));
            entries.put(key, entry);
        }
        return entry;
    }

    /**
     * Returns the meanings for the given entry. The length of the returned entry is the number
     * of languages (typically 2). Each entry is a comma-separated list of senses in one language.
     */
    private StringBuilder[] getMeanings(final int ent_seq) throws SQLException {
        assert Thread.holdsLock(this);
        final StringBuilder[] senses = new StringBuilder[localeCodes.length];
        selectMeaning.setInt(1, ent_seq);
        try (final ResultSet rs = selectMeaning.executeQuery()) {
            while (rs.next()) {
                final String lang = rs.getString(2);
                for (int i=0; i<localeCodes.length; i++) {
                    if (lang.equals(localeCodes[i])) {
                        StringBuilder buf = senses[i];
                        if (buf == null) {
                            senses[i] = buf = new StringBuilder();
                        } else {
                            buf.append(", ");
                        }
                        buf.append(rs.getString(3));
                    }
                }
            }
        }
        return senses;
    }

    /**
     * Returns all entries matching the given criterion.
     * Note: the array returned by this method may be cached - <strong>do not modify</strong>.
     *
     * @param  word The Kanji or reading word to search.
     * @return All entries for the given words.
     * @throws SQLException If an error occurred while querying the database.
     */
    public synchronized Entry[] search(final String word) throws SQLException {
        final PreparedStatement stmt;
        switch (CharacterType.forWord(word)) {
            case KANJI:    stmt = searchKanji; break;
            case KATAKANA: // Fallthrough
            case HIRAGANA: stmt = searchReading; break;
            default:       stmt = searchMeaning; break;
        }
        stmt.setString(1, exactMatch ? word : word + '%');
        final List<Entry> entries = new ArrayList<>();
        try (final ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                entries.add(getEntry(rs.getInt(1)));
            }
        }
        return entries.toArray(new Entry[entries.size()]);
    }

    /**
     * Closes the prepared statements used by this object.
     *
     * @throws SQLException If an error occurred while closing the statements.
     */
    @Override
    public synchronized void close() throws SQLException {
        entries.clear();
        searchKanji  .close();
        searchReading.close();
        searchMeaning.close();
        selectMeaning.close();
        selectEntry  .close();
        if (toClose != null) {
            toClose.close();
        }
    }
}
