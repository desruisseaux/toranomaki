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
import java.util.HashMap;
import fr.toranomaki.edict.Entry;


/**
 * An entry read from the XML file. This class contains temporary information which will not be
 * written in the binary file, or which will be written in a different way.
 *
 * @author Martin Desruisseaux
 */
final class XMLEntry extends Entry {
    /**
     * A unique numeric sequence number for each entry.
     *
     * @see ElementType#ent_seq
     */
    public final int identifier;

    /**
     * The synonyms and antonyms for an entry. Those values will be written only after we
     * finished to read every entries from the XML file, in order to resolve cross-references.
     */
    private Map<String, Boolean> xref;

    /**
     * The position of this entry in the binery stream, after the indexes.
     */
    int position;

    /**
     * Creates an initially empty entry.
     *
     * @param ent_seq A unique numeric sequence number for each entry.
     */
    public XMLEntry(final int ent_seq) {
        this.identifier = ent_seq;
    }

    /**
     * Adds the given string into the map of synonyms or antonyms. This is an helper method for
     * writing into the {@link #synonyms} and {@link #antonyms} maps. Those information will be
     * written to the binary file after all entries has been read.
     */
    final void addXRef(final String word, final boolean synonym) {
        Map<String, Boolean> xref = this.xref;
        if (xref == null) {
            this.xref = xref = new HashMap<>();
        }
        xref.put(word, synonym);
    }

    /**
     * Compares this entry with the given entry for priority order, or {@linkplain #identifier}
     * has a fallback if the two entries have the same priority.
     */
    @Override
    public int compareTo(final Entry other) {
        final int c = super.compareTo(other);
        if (c == 0) {
            final int id1 = ((XMLEntry) other).identifier;
            if (identifier < id1) return -1;
            if (identifier > id1) return +1;
        }
        return c;
    }
}
