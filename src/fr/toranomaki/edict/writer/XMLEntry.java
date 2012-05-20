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

import fr.toranomaki.edict.Entry;


/**
 * An entry read from the XML file. This class contains temporary information which will not be
 * written in the binary file, or which will be written in a different way.
 *
 * @todo Not yet completed. There is many fields we can move in this class, basically everything
 *       declared as <code>Map(XMLEntry, ...)</code> could be replaced by a field here.
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
     * Creates an initially empty entry.
     *
     * @param ent_seq A unique numeric sequence number for each entry.
     */
    public XMLEntry(final int ent_seq) {
        this.identifier = ent_seq;
    }
}
