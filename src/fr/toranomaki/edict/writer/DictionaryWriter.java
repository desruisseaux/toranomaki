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
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.Sense;
import fr.toranomaki.edict.DictionaryFile;
import fr.toranomaki.edict.DictionaryReader;

import static java.nio.file.StandardOpenOption.*;
import static fr.toranomaki.edict.writer.WordEncoder.writeFully;


/**
 * Writes the given {@link Entry} instances to the binary file.
 *
 * @author Martin Desruisseaux
 */
public final class DictionaryWriter extends DictionaryFile {
    /**
     * Set to {@code true} for verifying the binary file after writing.
     */
    private static final boolean VERIFY = true;

    /**
     * The file to create.
     */
    private final Path file;

    /**
     * The index of Japanese words.
     */
    private final WordTable japanese;

    /**
     * The index of translations.
     */
    private final WordTable senses;

    /**
     * The position of each entry in the stream, after the indexes.
     */
    private final Map<Entry, Integer> entryPositions;

    /**
     * Creates a new dictionary writers from the given entries.
     * This constructor creates the binary file immediately.
     */
    DictionaryWriter(final List<Entry> entries) throws IOException {
        file = getDirectory().resolve("JMdict.dat");
        final ByteBuffer buffer = ByteBuffer.allocate(1024 * NUM_BYTES_FOR_INDEX_ELEMENT);
        buffer.order(BYTE_ORDER);

        System.out.println("Creating index for Japanese words");
        final WordIndexWriter japanWriter = new WordIndexWriter(entries, true, buffer);

        System.out.println("Creating index for senses");
        final WordIndexWriter senseWriter = new WordIndexWriter(entries, false, buffer);

        System.out.println("Creating entry references");
        final WordToEntries japanToEntries = new WordToEntries(entries, true);
        final WordToEntries senseToEntries = new WordToEntries(entries, false);
        final EntryList[] entryLists = WordToEntries.computePositions(japanToEntries, senseToEntries);
        entryPositions = new IdentityHashMap<>(entries.size());
        final int entryPoolLength = computeEntryPositions(entries);

        System.out.println("Writing the dictionary file");
        try (FileChannel out = FileChannel.open(file, WRITE, CREATE, TRUNCATE_EXISTING)) {
            japanese = japanWriter.writeHeader(out);
            senses   = senseWriter.writeHeader(out);
            buffer.putInt(WordToEntries.entryListPoolSize(entryLists));
            buffer.putInt(entryPoolLength);
            japanWriter.writeIndex(japanese, out); japanToEntries.writeReferences(japanese.words, buffer, out);
            senseWriter.writeIndex(senses,   out); senseToEntries.writeReferences(senses  .words, buffer, out);
            WordToEntries.writeLists(entryLists, entryPositions, buffer, out);
            int position = 0;
            for (final Entry entry : entries) {
                assert entryPositions.get(entry) == position;
                position += writeEntry(entry, buffer, out);
            }
        }
    }

    /**
     * Fills the {@link #entryPositions} map with the expected position of all given entries.
     *
     * @return The total number of bytes needed for the entries pool.
     */
    private int computeEntryPositions(final List<Entry> entries) throws IOException {
        int position = 0;
        for (final Entry entry : entries) {
            if (entryPositions.put(entry, position) != null) {
                throw new IllegalArgumentException("Duplicated entry: " + entry);
            }
            position += writeEntry(entry, null, null);
        }
        return position;
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
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param  entry  The entry to write.
     * @param  buffer Where to write the entry, or {@code null} for computing the return value without writing the entry.
     * @param  out    Where to flush the buffer if it is full, or {@code null} if the {@code buffer} is null.
     * @return The number of bytes needed for writing the given entry.
     */
    private int writeEntry(final Entry entry, final ByteBuffer buffer, final WritableByteChannel out) throws IOException {
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
         */
        final int length = 2
                + (numKanjis + numReadings) * ((Integer.SIZE + Short.SIZE) / Byte.SIZE)
                + senses.length * ((Integer.SIZE) / Byte.SIZE);
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
            boolean isKanji = false;
            do {
                final int count = isKanji ? numKanjis : numReadings;
                for (int i=0; i<count; i++) {
                    buffer.putInt(japanese.getPackedPosition(entry.getWord(isKanji, i)));
                    buffer.putShort(entry.getPriority(isKanji, i));
                }
            } while ((isKanji = !isKanji) == true);
            for (final Sense sense : senses) {
                buffer.putInt(this.senses.getPackedPosition(sense.meaning));
            }
            assert (verify = buffer.position() - verify - length) == 0 : verify;
        }
        return length;
    }

    /**
     * Verifies all index.
     */
    void verifyIndex() throws IOException {
        System.out.println("Verifying index");
        final DictionaryReader reader = new DictionaryReader(file);
        verifyIndex(reader, japanese, true);
        verifyIndex(reader, senses,  false);
    }

    /**
     * Verifies the index for all words in the given table.
     *
     * @param reader   The reader to use for verifying the index.
     * @param table    The table of words to verify.
     * @param japanese {@code true} for verifying Japanese words, or {@code false} for verifying senses.
     */
    private static void verifyIndex(final DictionaryReader reader, final WordTable table, final boolean japanese) throws IOException {
        final String[] words = table.words.clone();
        Collections.shuffle(Arrays.asList(words));
        for (int i=0; i<words.length; i++) {
            final String word  = words[i];
            final Entry  entry = reader.search(word, japanese);
//            final String found = japanese ? entry.getWord(false, 0) : entry.getSenses()[0].meaning;
            final String found = entry.getWord(false, 0);
            if (!word.equals(found)) {
                throw new IOException("Verification failed: expected \"" + word + "\" but found \"" + found + "\".");
            }
        }
    }

    /**
     * Run the JMdict import from the command line.
     *
     * @param  args The command line arguments.
     * @throws Exception If a I/O, SAX or other exception occurred.
     */
    public static void main(final String[] args) throws Exception {
        final XMLParser parser = new XMLParser();
        try (InputStream in = XMLParser.getDefaultStream()) {
            parser.parse(in);
        }
        final DictionaryWriter writer = new DictionaryWriter(parser.entryList);
        if (VERIFY) {
            writer.verifyIndex();
        }
    }
}
