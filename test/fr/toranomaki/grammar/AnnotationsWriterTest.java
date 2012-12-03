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
package fr.toranomaki.grammar;

import java.io.IOException;
import fr.toranomaki.edict.DictionaryReader;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Tests the {@link AnnotationsWriter} class.
 *
 * @author Martin Desruisseaux
 */
public final class AnnotationsWriterTest {
    /**
     * Tests writing a few annotations in a simple class.
     *
     * @throws IOException If an error occurred while reading the dictionary.
     */
    @Test
    public void testSimple() throws IOException {
        final AnnotationsWriter aw = new AnnotationsWriter(new DictionaryReader());
        final StringBuilder text = new StringBuilder("明日、公園へ行く。");
        aw.annotate(text);
        assertEquals("明日〖あした〗、公園〖こうえん〗へ行く〖いく〗。", text.toString());
    }
}
