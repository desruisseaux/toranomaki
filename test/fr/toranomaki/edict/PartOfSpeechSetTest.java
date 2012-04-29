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

import java.util.Set;
import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Tests the {@link PartOfSpeechSet} class.
 *
 * @author Martin Desruisseaux
 */
public final class PartOfSpeechSetTest {
    /**
     * Computes the code for the given set of <cite>Part Of Speech</cite>.
     * The code in this method is a copy of the code in the
     * {@link fr.toranomaki.edict.writer.DictionaryWriter#computePartOfSpeechMap(Set)}
     * method.
     */
    private static long computeCode(final Set<PartOfSpeech> posSet) {
        long code = 0;
        int bitOffset = 0;
        for (final PartOfSpeech pos : posSet) {
            assertTrue(bitOffset < Long.SIZE);
            final long ordinal = pos.ordinal() + 1;
            assertTrue(ordinal <= 0xFF);
            code |= (ordinal << bitOffset);
            bitOffset += Byte.SIZE;
        }
        return code;
    }

    /**
     * Tests an empty set.
     */
    @Test
    public void testEmpty() {
        final Set<PartOfSpeech> posSet = EnumSet.noneOf(PartOfSpeech.class);
        assertEquals("[]", posSet.toString());
        final long code = computeCode(posSet);
        assertEquals(0, code);
        final Set<PartOfSpeech> verify = new PartOfSpeechSet(code);
        assertEquals(0, verify.size());
        assertEquals(posSet, verify);
    }

    /**
     * Tests a set with two values.
     */
    @Test
    public void testTwoValues() {
        final Set<PartOfSpeech> posSet = EnumSet.of(PartOfSpeech.ADVERB, PartOfSpeech.PARTICLE);
        assertEquals("[adverb, particle]", posSet.toString());
        final long code = computeCode(posSet);
        assertEquals(5899, code);
        final Set<PartOfSpeech> verify = new PartOfSpeechSet(code);
        assertEquals(2, verify.size());
        assertEquals(posSet, verify);
    }
}
