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
package fr.toranomaki;

import java.util.Set;
import java.util.EnumSet;
import java.sql.SQLException;
import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.JMdict;
import fr.toranomaki.edict.Priority;


/**
 * A row to be shown in the {@link WordTable}. This row combine the {@link Entry} to be
 * shown, together with some rendering information.
 *
 * @author Martin Desruisseaux
 */
final class WordElement {
    /**
     * When an entry contains many word elements, the element to show. This constant is
     * used for making easier to identify the places in the code where only the first
     * element is show and the other elements are ignored.
     */
    static final int WORD_INDEX = 0;

    /**
     * If a word has a priority of at least one of the given types, it will be considered common.
     */
    private static final Set<Priority.Type> COMMONS = EnumSet.of(
            Priority.Type.news, Priority.Type.ichi, Priority.Type.spec);

    /**
     * The EDICT entry to be show.
     */
    final Entry entry;

    /**
     * {@code true} if the Kanji element of the {@linkplain #entry} is common.
     */
    final boolean isCommonKanji;

    /**
     * {@code true} if the reading element of the {@linkplain #entry} is common.
     */
    final boolean isCommonReading;

    /**
     * Creates a new row for the given entry.
     *
     * @param  dictionary   The dictionary used for building the entry.
     * @param  entry        The entry.
     * @throws SQLException If an error occurred while fetching additional information
     *                      from the database.
     */
    WordElement(final JMdict dictionary, final Entry entry) throws SQLException {
        this.entry      = entry;
        isCommonKanji   = isCommon(dictionary, entry, true);
        isCommonReading = isCommon(dictionary, entry, false);
    }

    /**
     * Returns {@code true} if the Kanji or reading element is common.
     */
    private static boolean isCommon(final JMdict dictionary, final Entry entry, final boolean isKanji) throws SQLException {
        final short code = entry.getPriority(isKanji, WORD_INDEX);
        if (code != 0) {
            for (final Priority priority : dictionary.getPriority(code)) {
                if (COMMONS.contains(priority.type)) {
                    return true;
                }
            }
        }
        return false;
    }
}
