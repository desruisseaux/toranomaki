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

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;


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
     * {@code true} if this dictionary is looking for exact matches.
     */
    private final boolean exact;

    /**
     * The connection to the SQL database to be closed by the {@link #close()} method,
     * or {@code null} if this class doesn't own the connection.
     */
    private final Connection toClose;

    /**
     * The statement for searching a Kanji element.
     */
    private final PreparedStatement searchKanji;

    /**
     * The statement for searching a reading element.
     */
    private final PreparedStatement searchReading;

    /**
     * Returns the datasource to use for the connection to the SQL database.
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
        this.exact = exact;
        toClose       = exact ? null : connection; // For now, close only if invoked from the public constructor.
        searchKanji   = prepareSelect(connection, TableOrColumn.entries, ElementType.keb, ElementType.ent_seq);
        searchReading = prepareSelect(connection, TableOrColumn.entries, ElementType.reb, ElementType.ent_seq);
    }

    /**
     * Creates a {@code SELECT} statement into the given table using the given search criterion.
     *
     * @param  connection The connection to the SQL database.
     * @param  table      The table where the values will be searched.
     * @return The prepared statement.
     * @throws SQLException If an error occurred while preparing the statement.
     */
    private PreparedStatement prepareSelect(final Connection connection, final TableOrColumn table,
            final Enum<?> criterion, final Enum<?> orderBy) throws SQLException
    {
        final StringBuilder sql = new StringBuilder();
        String separator = "SELECT ";
        for (final Enum<?> column : table.columns) {
            sql.append(separator).append(column);
            separator = ", ";
        }
        sql.append(" FROM ").append(table).append(" WHERE ").append(criterion)
                .append(exact ? " = " : " LIKE ").append('?').append(" ORDER BY ").append(orderBy);
        return connection.prepareStatement(sql.toString());
    }

    /**
     * Returns all entries matching the given criterion.
     * Note: the array returned by this method may be cached - <strong>do not modify</strong>.
     *
     * @param  word The Kanji or reading word to search.
     * @return All entries for the given words.
     * @throws SQLException If an error occurred while querying the database.
     */
    public Entry[] search(final String word) throws SQLException {
        final boolean isKanji = ElementType.isIdeographic(word);
        final PreparedStatement stmt = isKanji ? searchKanji : searchReading;
        stmt.setString(1, exact ? word : word + '%');
        final List<Entry> entries = new ArrayList<>();
        try (final ResultSet rs = stmt.executeQuery()) {
            Entry entry = null;
            while (rs.next()) {
                final int seq = rs.getInt(1);
                if (entry == null || entry.identifier != seq) {
                    entry = new Entry(seq);
                    entries.add(entry);
                }
                boolean isKanjiResult = true;
                do { // This loop is executed twice: once for Kanji and once for reading element.
                    final String found = rs.getString(isKanjiResult ? 2 : 3);
                    if (!rs.wasNull()) {
                        short priority = rs.getShort(isKanjiResult ? 4 : 5);
                        if (rs.wasNull()) {
                            priority = 0;
                        }
                        entry.add(isKanjiResult, found, priority);
                    }
                } while ((isKanjiResult = !isKanjiResult) == false);
            }
        }
        /*
         * For each entry, sort the elements by priority order.
         */
        final Entry[] array = entries.toArray(new Entry[entries.size()]);
        for (final Entry entry : array) {
            entry.sortByPriority(true);
            entry.sortByPriority(false);
        }
        return array;
    }

    /**
     * Closes the prepared statements used by this object.
     *
     * @throws SQLException If an error occurred while closing the statements.
     */
    @Override
    public void close() throws SQLException {
        searchKanji.close();
        searchReading.close();
        if (toClose != null) {
            toClose.close();
        }
    }
}
