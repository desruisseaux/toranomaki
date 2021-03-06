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

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.Alphabet;
import fr.toranomaki.edict.PartOfSpeech;
import fr.toranomaki.edict.BinaryData;
import fr.toranomaki.edict.DictionaryReader;

import static java.nio.file.StandardOpenOption.*;
import static fr.toranomaki.edict.writer.WordEncoder.writeFully;


/**
 * Writes the given {@link XMLEntry} instances to the binary file.
 *
 * @author Martin Desruisseaux
 */
public final class DictionaryWriter extends BinaryData {
    /**
     * The file to create, typically {@link #getDictionaryFile()}.
     */
    private final Path file;

    /**
     * The index of words for each languages (currently only Japanese and westerners).
     */
    private final WordTable[] wordTables;

    /**
     * Index of each set of <cite>Part of speech</cite>.
     */
    private final Map<Set<PartOfSpeech>, Integer> partOfSpeechMap;

    /**
     * Creates a new dictionary writers from the given entries.
     * This constructor creates the binary file immediately.
     */
    private DictionaryWriter(final List<XMLEntry> entries, final Set<Set<PartOfSpeech>> posSets) throws IOException {
        file = getDictionaryFile();
        final ByteBuffer buffer = ByteBuffer.allocate(1024 * NUM_BYTES_FOR_INDEX_ELEMENT);
        buffer.order(BYTE_ORDER);
        /*
         * Prepares the index of Japanese words, and the index of sense words.
         * The 'WordIndexWriter' objects created here have enough information
         * for writing the header of the binary file. They also contains the
         * actual words in an encoded form which will be written later.
         */
        final Alphabet[] alphabets = Alphabet.values();
        final WordIndexWriter[] wordIndex = new WordIndexWriter[2];
        for (int i=0; i<wordIndex.length; i++) {
            System.out.println("Creating index for " + ((i == 0) ? "Japanese words" : "senses"));
            wordIndex[i] = new WordIndexWriter(entries, alphabets[i], buffer);
        }
        /*
         * Computes how many bytes will be needed for each entry, and prepares a map of
         * entry index to the actual position in the stream which will contains that entry.
         */
        System.out.println("Creating entry references");
        final WordToEntries[] wordToEntries = new WordToEntries[wordIndex.length];
        for (int i=0; i<wordToEntries.length; i++) {
            wordToEntries[i] = new WordToEntries(entries, alphabets[i]);
        }
        final EntryListPool entryListPool = WordToEntries.computePositions(wordToEntries);
        final int entryPoolLength = computeEntryPositions(entries);
        /*
         * In order to write the entries, we will need references to a set of "part of speech".
         * There is relatively few distincts sets (about 400), and each set contains few elements
         * (no more than 8), so it is worth to declare all the sets in the header so we can refer
         * to them by a single byte.
         */
        partOfSpeechMap = new HashMap<>(posSets.size() * 2);
        final long[] partOfSpeechCodes = computePartOfSpeechMap(posSets);
        /*
         * Now process to the actual file creation...
         */
        System.out.println("Writing the dictionary file");
        wordTables = new WordTable[2];
        try (FileChannel out = FileChannel.open(file, WRITE, CREATE, TRUNCATE_EXISTING)) {
            for (int i=0; i<wordIndex.length; i++) {
                wordTables[i] = wordIndex[i].writeHeader(out);
            }
            buffer.putInt(entryListPool.size);
            buffer.putInt(entryPoolLength);
            buffer.putInt(partOfSpeechCodes.length);
            for (final long code : partOfSpeechCodes) {
                buffer.putLong(code);
            }
            /*
             * At this point, the header has been written. Now write the file
             * portion which will be mapped by a direct buffer at reading time.
             */
            for (int i=0; i<wordIndex.length; i++) {
                wordIndex[i].writeIndex(wordTables[i], out);
                wordToEntries[i].write(wordTables[i].words, buffer, out);
            }
            entryListPool.write(buffer, out);
            int position = 0;
            for (final XMLEntry entry : entries) {
                assert entry.position == position : position;
                position += writeEntry(entry, buffer, out);
            }
            assert position == entryPoolLength;
            writeFully(buffer, out);
        }
    }

    /**
     * Fills the {@link #entryPositions} map with the expected position of all given entries.
     *
     * @return The total number of bytes needed for the entries pool.
     * @throws IOException Should never happen, since this method is not performing real write
     *         operations. This exception is declared only because this method "simulate" write
     *         operations in order to compute the expected pool size.
     */
    private int computeEntryPositions(final List<XMLEntry> entries) throws IOException {
        int position = 0;
        for (final XMLEntry entry : entries) {
            assert entry.position == 0 : entry;
            entry.position = position;
            position += writeEntry(entry, null, null);
        }
        return position;
    }

    /**
     * Fills the {@link #partOfSpeechMap} map.
     */
    private long[] computePartOfSpeechMap(final Set<Set<PartOfSpeech>> posSets) {
        final int count = posSets.size();
        int i = 0;
        final long[] partOfSpeechCodes = new long[count];
        final Map<Long, Set<PartOfSpeech>> codeMapping = new HashMap<>(count * 2);
        for (final Set<PartOfSpeech> posSet : posSets) {
            long code = 0;
            int bitOffset = 0;
            for (final PartOfSpeech pos : posSet) {
                if (bitOffset >= Long.SIZE) {
                    throw new IllegalArgumentException("Too many PartOfSpeech: " + posSet);
                }
                final long ordinal = pos.ordinal() + 1;
                if (ordinal > 0xFF) {
                    throw new IllegalArgumentException("Ordinal value of " + pos + " is too high.");
                }
                code |= (ordinal << bitOffset);
                bitOffset += Byte.SIZE;
            }
            partOfSpeechCodes[i++] = code;
            if (codeMapping.put(code, posSet) != null) {
                throw new AssertionError(posSet); // Paranoiac check.
            }
        }
        Arrays.sort(partOfSpeechCodes);
        for (i=0; i<count; i++) {
            partOfSpeechMap.put(codeMapping.get(partOfSpeechCodes[i]), i);
        }
        assert !partOfSpeechMap.containsKey(null);
        return partOfSpeechCodes;
    }

    /**
     * Writes a single entry to the given buffer. The format is:
     * <p>
     * <ul>
     *   <li>Number of Kanji elements, on {@value #NUM_BITS_FOR_ELEMENT_COUNT} bits.</li>
     *   <li>Number of reading elements, on {@value #NUM_BITS_FOR_ELEMENT_COUNT} bits.</li>
     *   <li>Number of senses, on a {@code byte}.</li>
     *   <li>For each Kanji elements, followed by each reading elements:
     *     <ul>
     *       <li>Packed index of the word, as an {@code int}.</li>
     *       <li>Priority (0 if none), as a {@code short}.</li>
     *     </ul>
     *   </li>
     *   <li>For each sense:
     *     <ul>
     *       <li>Packed index of the word, as an {@code int}.</li>
     *       <li>The language as an index in the {@link #LANGUAGES} array, on 3 bits.</li>
     *       <li>Reference to the set of Part of Speech (POS), on 13 bits.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param  entry  The entry to write.
     * @param  buffer Where to write the entry, or {@code null} for computing the return value without writing the entry.
     * @param  out    Where to flush the buffer if it is full, or {@code null} if the {@code buffer} is null.
     * @return The number of bytes needed for writing the given entry.
     */
    private int writeEntry(final XMLEntry entry, final ByteBuffer buffer, final WritableByteChannel out) throws IOException {
        final int numKanjis   = entry.getCount(true);
        final int numReadings = entry.getCount(false);
        final Sense[] senses  = entry.getSenses();
        if (numKanjis     >= (1 << NUM_BITS_FOR_ELEMENT_COUNT) ||
            numReadings   >= (1 << NUM_BITS_FOR_ELEMENT_COUNT) ||
            senses.length >= (1 << Byte.SIZE))
        {
            throw new IllegalArgumentException(entry.toString());
        }
        /*
         * Computes the expected length in bytes as:
         *   - Number of (Kanji, reading, senses), packed as described in the javadoc
         *   - Packed referenced to Kanki/reading elements (as int)
         *   - Packed referenced to senses (as int)
         *   - Language and Part Of Speechs (1 short per sense).
         */
        int length = 2
                + ((Integer.SIZE + Short.SIZE) / Byte.SIZE) * (numKanjis + numReadings)
                + ((Integer.SIZE + Short.SIZE) / Byte.SIZE) * senses.length;
        /*
         * Actual writing.
         */
        if (buffer != null) {
            if (buffer.remaining() < length) {
                writeFully(buffer, out);
            }
            int verify = buffer.position(); // To be used for assertions only.
            buffer.put((byte) ((numKanjis << NUM_BITS_FOR_ELEMENT_COUNT) | numReadings));
            buffer.put((byte) senses.length);
            final WordTable japaneseWords = wordTables[Alphabet.JAPANESE.ordinal()];
            final WordTable senseWords    = wordTables[Alphabet.LATIN.ordinal()];
            boolean isKanji = true;
            do {
                final int count = isKanji ? numKanjis : numReadings;
                for (int i=0; i<count; i++) {
                    buffer.putInt(japaneseWords.getPackedPosition(entry.getWord(isKanji, i)));
                    buffer.putShort(entry.getPriority(isKanji, i));
                }
            } while ((isKanji = !isKanji) == false);
            /*
             * After Kanjis and reading elements, now write senses.
             */
            for (final Sense sense : senses) {
                buffer.putInt(senseWords.getPackedPosition(sense.meaning));
                int n = partOfSpeechMap.get(sense.partOfSpeech);
                if (n >= (1 << (Short.SIZE - NUM_BITS_FOR_LANGUAGE))) {
                    throw new IllegalArgumentException("Too many set of Part of Speech: " + n);
                }
                n = (n << NUM_BITS_FOR_LANGUAGE) | languageIndex(sense.locale);
                buffer.putShort((short) n);
            }
            assert (verify = buffer.position() - verify - length) == 0 : verify;
        }
        return length;
    }

    /**
     * Returns the index of the given language.
     */
    private static int languageIndex(final Locale language) {
        for (int i=0; i<LANGUAGES.length; i++) {
            if (LANGUAGES[i] == language) {
                assert i < (1 << NUM_BITS_FOR_LANGUAGE) : i;
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown language: " + language);
    }

    /**
     * Verifies all index, then verifies entries.
     */
    private void verify(final List<XMLEntry> entries) throws IOException {
        System.out.println("Verifying index");
        final DictionaryReader reader = new DictionaryReader(file, LANGUAGES);
        final Alphabet[] alphabets = Alphabet.values();
        for (int i=0; i<wordTables.length; i++) {
            final WordTable table = wordTables[i];
            final Alphabet alphabet = alphabets[i];
            final String[] words = table.words.clone();
            Collections.shuffle(Arrays.asList(words));
            for (final String word : words) {
                final String found = reader.getWordAt(alphabet, reader.getWordIndex(alphabet, word));
                if (!word.equals(found)) {
                    throw new IOException("Verification failed: expected \"" + word + "\" but found \"" + found + "\".");
                }
            }
        }
        System.out.println("Verifying entries");
        for (final XMLEntry expected : entries) {
            final Entry actual = reader.getEntryAt(expected.position);
            boolean isKanji = true;
            do {
                final int n = expected.getCount(isKanji);
                assertEquals(expected, "getCount(" + isKanji + ')', n, actual.getCount(isKanji));
                for (int i=0; i<n; i++) {
                    assertEquals(expected, "getWord(" + isKanji + ',' + i + ')',
                            expected.getWord(isKanji, i), actual.getWord(isKanji, i));
                    assertEquals(expected, "getPriority(" + isKanji + ',' + i + ')',
                            expected.getPriority(isKanji, i), actual.getPriority(isKanji, i));
                }
            } while ((isKanji = !isKanji) == false);
            final Sense[] se = expected.getSenses();
            final Sense[] sa = actual  .getSenses();
            assertEquals(expected, "getSenses().length", se.length, sa.length);
            for (int i=0; i<se.length; i++) {
                assertEquals(expected, "getSenses()[" + i + "].locale",       se[i].locale,       sa[i].locale);
                assertEquals(expected, "getSenses()[" + i + "].meaning",      se[i].meaning,      sa[i].meaning);
                assertEquals(expected, "getSenses()[" + i + "].partOfSpeech", se[i].partOfSpeech, se[i].partOfSpeech);
            }
        }
    }

    /**
     * Ensures that the two given values are equal.
     *
     * @param entry         The entry, used only for formatting an error message.
     * @param comparedValue The method for which we are testing the value.
     * @param expected      The expected value.
     * @param actual        The actual value.
     * @throws IOException  If the actual value is not equals to the expected value.
     */
    private static void assertEquals(final XMLEntry entry, final String comparedValue,
            final Object expected, final Object actual) throws IOException
    {
        if (!expected.equals(actual)) {
            throw new IOException("Verification failed for " + comparedValue +
                    ": expected \"" + expected + "\" " + " for " + entry +
                    " but got \"" + actual + "\".");
        }
    }

    /**
     * Run the JMdict import from the command line.
     *
     * @param  args The command line arguments.
     * @throws Exception If a I/O, SAX or other exception occurred.
     */
    public static void main(final String[] args) throws Exception {
        final XMLParser parser = new XMLParser(LANGUAGES);
        try (InputStream in = XMLParser.getDefaultStream()) {
            parser.parse(in);
        }
        final DictionaryWriter writer = new DictionaryWriter(parser.entryList, parser.getPartOfSpeechSets());
        writer.verify(parser.entryList);
    }
}
