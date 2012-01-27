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
     * The statement for searching a Kanji element.
     */
    private final PreparedStatement searchKanji;

    /**
     * The statement for searching a reading element.
     */
    private final PreparedStatement searchReading;

    /**
     * Creates a new connection to the dictionary.
     *
     * @param  connection The connection to the database.
     * @param  exact {@code true} if the search methods should look for exact matches.
     * @throws SQLException If an error occurred while preparing the statements.
     */
    public JMdict(final Connection connection, final boolean exact) throws SQLException {
        this.exact = exact;
        searchKanji   = prepareSelect(connection, TableOrColumn.entries, ElementType.keb);
        searchReading = prepareSelect(connection, TableOrColumn.entries, ElementType.reb);
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
            final Enum<?>... criterions) throws SQLException
    {
        final StringBuilder sql = new StringBuilder();
        String separator = "SELECT ";
        for (final Enum<?> column : table.columns) {
            sql.append(separator).append(column);
            separator = ", ";
        }
        sql.append(" FROM ").append(table);
        separator = " WHERE ";
        for (final Enum<?> column : criterions) {
            sql.append(separator).append(column).append(exact ? " = " : " LIKE ").append('?');
            separator = " AND ";
        }
        return connection.prepareStatement(sql.toString());
    }

    /**
     * Returns all entries matching the given criterion.
     *
     * @param  word The Kanji or reading word to search.
     * @return All entries for the given words.
     * @throws SQLException If an error occurred while querying the database.
     */
    public List<Entry> search(final String word) throws SQLException {
        final PreparedStatement stmt = ElementType.isIdeographic(word) ? searchKanji : searchReading;
        stmt.setString(1, word);
        final List<Entry> entries = new ArrayList<>();
        try (final ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                final int seq = rs.getInt(1);
                final Entry entry = new Entry(seq);
                entries.add(entry);
            }
        }
        return entries;
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
    }
}
