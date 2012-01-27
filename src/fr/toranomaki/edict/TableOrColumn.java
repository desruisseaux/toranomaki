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

import static fr.toranomaki.edict.ElementType.*;


/**
 * Some elements not found in the XML file, but declared in the SQL database.
 * This enumeration is used as a complement of {@link ElementType}. It is not
 * public because those implementation details may change in any future version.
 * <p>
 * We do not distinguish between tables and columns because this is not yet ready,
 * but this approach may change in the future.
 *
 * @author Martin Desruisseaux
 */
enum TableOrColumn {
    /**
     * An identifier column which is not {@link ElementType#ent_seq}.
     */
    id((TableOrColumn[]) null),

    /**
     * The {@code "element"} column in the {@code "information"} table.
     */
    element((TableOrColumn[]) null),

    /**
     * The {@code "description"} column in the {@code "information"} table.
     */
    description((TableOrColumn[]) null),

    /**
     * The {@code xml:lang} attribute in a {@link ElementType#gloss} element.
     */
    lang((TableOrColumn[]) null),

    /**
     * The {@code "entries"} table.
     */
    entries(ent_seq, keb, reb, ke_pri, re_pri),

    /**
     * The {@code "pos"} table (<cite>Part Of Speech</cite>).
     */
    pos(id, description),

    /**
     * The {@code "priorities"} table.
     */
    priorities(addId(Priority.Type.values())),

    /**
     * The {@code "information"} table.
     */
    information(ent_seq, element, description),

    /**
     * The {@code "senses"} table for word meanings.
     */
    senses(ent_seq, pos, lang, gloss),

    /**
     * The {@code "references"} table for synonyms and antonyms.
     */
    xref(ent_seq, ElementType.xref, ant);

    /**
     * The columns, or {@code null} if this database entity is itself a column.
     */
    final Enum<?>[] columns;

    /**
     * Creates a new entity type with the given columns.
     *
     * @param columns The columns, or {@code null} if this database entity is itself a column.
     */
    private TableOrColumn(final Enum<?>... columns) {
        this.columns = columns;
    }

    /**
     * Adds an {@code "id"} column in from of the given column names.
     */
    private static Enum<?>[] addId(final Enum<?>[] columns) {
        final Enum<?>[] copy = new Enum<?>[columns.length + 1];
        System.arraycopy(columns, 0, copy, 1, columns.length);
        copy[0] = id;
        return copy;
    }
}
