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

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.DictionaryFile;
import fr.toranomaki.edict.DictionaryReader;

import static java.nio.file.StandardOpenOption.*;


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
     * Creates a new dictionary writers from the given entries.
     * This constructor creates the binary file immediately.
     */
    DictionaryWriter(final List<Entry> entries) throws IOException {
        file = getDirectory().resolve("JMdict.dat");
        final ByteBuffer buffer = ByteBuffer.allocate(1024 * ELEMENT_SIZE);
        buffer.order(BYTE_ORDER);

        System.out.println("Creating index for Japanese words");
        final WordIndexWriter japanWriter = new WordIndexWriter(entries, true, buffer);

        System.out.println("Creating index for senses");
        final WordIndexWriter senseWriter = new WordIndexWriter(entries, false, buffer);

        System.out.println("Creating dictionary file");
        try (FileChannel out = FileChannel.open(file, WRITE, CREATE, TRUNCATE_EXISTING)) {
            japanese = japanWriter.writeHeader(out);
            senses   = senseWriter.writeHeader(out);
            japanWriter.writeIndex(japanese, out);
            senseWriter.writeIndex(senses,   out);
            for (final Entry entry : entries) {
                writeEntry(entry, out, buffer);
            }
        }
    }

    private void writeEntry(final Entry entry, final WritableByteChannel out, final ByteBuffer buffer) throws IOException {
        // TODO
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
