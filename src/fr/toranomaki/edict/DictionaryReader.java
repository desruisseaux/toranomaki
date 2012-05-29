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

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Locale;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import fr.toranomaki.grammar.AugmentedEntry;


/**
 * The reader of the dictionary binary file.
 *
 * @author Martin Desruisseaux
 */
public class DictionaryReader extends BinaryData {
    /**
     * The cache capacity. This value is arbitrary, but we are better to use a value
     * not greater than a power of 2 time the load factor (0.75).
     */
    private static final int CACHE_SIZE = 3000;

    /**
     * The locales for which to search meanings, in <strong>reverse</strong> of preference order.
     * We use reverse order because the English is the most extensively used language in the EDICT
     * dictionary, so it is worth to put it first in our data structure. But it still only the
     * fallback language for non-English users.
     * <p>
     * The default values are {@linkplain Locale#ENGLISH English} followed by the
     * {@linkplain Locale#getDefault() system default}, if different then English.
     */
    private final Locale[] languages;

    /**
     * The index. For now we support only Japanese language and senses in westerner languages.
     * But we define this field as an array anyway in order to make easier the addition of new
     * languages in a future version, if desired.
     */
    private final WordIndexReader[] wordIndex;

    /**
     * A view over a portion of the file created by the {@link fr.toranomaki.edict.writer}
     * package. This is a view of all the remaining part of the binary file after the header.
     */
    final ByteBuffer buffer;

    /**
     * Index in the {@linkplain #buffer} where the list of entries begin.
     */
    final int entryListsPoolStart;

    /**
     * Index in the {@linkplain #buffer} where the definition of entries begin.
     */
    private final int entryDefinitionsStart;

    /**
     * The part of speech sets used in the binary file.
     */
    private final PartOfSpeechSet[] partOfSpeechSets;

    /**
     * A cache of most recently used entries. The cache capacity is arbitrary, but we are
     * better to use a value not greater than a power of 2 time the load factor (0.75).
     */
    @SuppressWarnings("serial")
    private final Map<Integer,AugmentedEntry> cachedEntries = new LinkedHashMap<Integer,AugmentedEntry>(1024, 0.75f, true) {
        @Override protected boolean removeEldestEntry(final Map.Entry<Integer,AugmentedEntry> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * Creates a new reader for the default binary file.
     *
     * @throws IOException If an error occurred while reading the file.
     */
    public DictionaryReader() throws IOException {
        this(getDictionaryFile(), getLanguages());
    }

    /**
     * Creates a new reader for the given binary file.
     *
     * @param  file The dictionary file to open, typically {@link #getDictionaryFile()}.
     * @param  languages The languages to use, in reverse of preference order.
     * @throws IOException If an error occurred while reading the file.
     */
    public DictionaryReader(final Path file, final Locale[] languages) throws IOException {
        this.languages = languages;
        final Alphabet[] alphabets = Alphabet.values();
        wordIndex = new WordIndexReader[2];
        final ByteBuffer header = ByteBuffer.allocate(4096);
        header.order(BYTE_ORDER);
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            /*
             * Initialize the index of words. Note that the WordIndexReader
             * constructor will read more data beyond the 'header' buffer.
             */
            long position = 0;
            for (int i=0; i<wordIndex.length; i++) {
                header.clear().limit(4 * (Integer.SIZE / Byte.SIZE) +
                                     1 * (Short  .SIZE / Byte.SIZE));
                readFully(in, header);
                wordIndex[i] = new WordIndexReader(this, in, header, alphabets[i], position);
                position = wordIndex[i].bufferEndPosition();
            }
            /*
             * Read remaining header data, then constructs the sets of Part Of Speech (POS).
             * We should have a raisonably small amount of set of POS (about 400).
             */
            header.clear().limit(3 * (Integer.SIZE / Byte.SIZE));
            readFully(in, header);
            entryListsPoolStart   = (int) position; position += header.getInt();
            entryDefinitionsStart = (int) position; position += header.getInt();
            partOfSpeechSets      = new PartOfSpeechSet[header.getInt()];
            header.clear().limit(partOfSpeechSets.length * (Long.SIZE / Byte.SIZE));
            readFully(in, header);
            for (int i=0; i<partOfSpeechSets.length; i++) {
                partOfSpeechSets[i] = new PartOfSpeechSet(header.getLong());
            }
            /*
             * Map the buffer.
             */
            buffer = in.map(FileChannel.MapMode.READ_ONLY, in.position(), position);
            buffer.order(BYTE_ORDER);
        }
    }

    /**
     * Searches the index of the given word. If no exact match is found, returns the
     * "insertion point" with all bits reversed (same convention than
     * {@link java.util.Arrays#binarySearch(Object[], Object)}).
     *
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @param  word The word to search.
     * @return The index of the given word, or the insertion point with all bits reversed.
     */
    public final synchronized int getWordIndex(final Alphabet alphabet, final String word) {
        return wordIndex[alphabet.ordinal()].getWordIndex(word);
    }

    /**
     * Returns the word at the given index. The index is typically a value returned by
     * {@link #getWordIndex(String, boolean)}.
     *
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @param  wordIndex Index of the word to search.
     * @return The word at the given index.
     * @throws IndexOutOfBoundsException If the given index is out of bounds.
     */
    public final synchronized String getWordAt(final Alphabet alphabet, final int wordIndex) throws IndexOutOfBoundsException {
        return this.wordIndex[alphabet.ordinal()].getWordAt(wordIndex);
    }

    /**
     * Gets the entry at the given position. This method is for internal usage, either invoked by
     * {@link fr.toranomaki.edict.writer.DictionaryWriter} for verification purpose, or invoked as
     * a callback method for {@link WordIndexReader}.
     *
     * @param  position Entry position, in bytes relative to the beginning of the entry pool.
     * @return The entry at the given index.
     */
    public final synchronized AugmentedEntry getEntryAt(final int position) {
        final Integer key = position;
        AugmentedEntry entry = cachedEntries.get(key);
        if (entry != null) {
            return entry;
        }
        buffer.position(entryDefinitionsStart + position);
        /*
         * Get the number of Japanese words and the number of senses.
         */
        int numKanjis = buffer.get() & 0xFF;
        final int numReadings = numKanjis & ((1 << NUM_BITS_FOR_ELEMENT_COUNT) - 1);
        numKanjis >>>= NUM_BITS_FOR_ELEMENT_COUNT;
        final int numJapaneses = numKanjis + numReadings;
        final int numSenses = buffer.get() & 0xFF;
        /*
         * Extract all entry data now. This include pointer to words,
         * but we will not resolve those pointers yet.
         */
        final int[]   wordRefs   = new int  [numJapaneses + numSenses];
        final short[] attributes = new short[numJapaneses + numSenses];
        for (int i=0; i<wordRefs.length; i++) {
            wordRefs  [i] = buffer.getInt();
            attributes[i] = buffer.getShort();
        }
        /*
         * Now build the entry. Note that the call to WordIndexReader.getWordAtPacked(int)
         * will change the buffer position, which is why we needed to extract all pointers
         * first.
         */
        entry = new AugmentedEntry();
        WordIndexReader index = wordIndex[Alphabet.JAPANESE.ordinal()];
        for (int i=0; i<numJapaneses; i++) {
            entry.add(i < numKanjis, index.getWordAtPacked(wordRefs[i]), attributes[i]);
        }
        index = wordIndex[Alphabet.LATIN.ordinal()];
        for (int i=numJapaneses; i<wordRefs.length; i++) {
            final String word = index.getWordAtPacked(wordRefs[i]);
            final short  attr = attributes[i];
            final int    lang = (attr & ((1 << NUM_BITS_FOR_LANGUAGE) - 1));
            entry.addSense(new Sense(LANGUAGES[lang], word, partOfSpeechSets[attr >>> NUM_BITS_FOR_LANGUAGE]));
        }
        // Undocumented behavior: do not add a summary sense and do not sort the senses in
        // preference order if the languages are exactly the ones saved in the file, because
        // this situation occurs only when we want to verify the integrity of the binary file.
        if (languages != LANGUAGES) {
            ((Entry) entry).addSenseSummary(languages);
        }
        entryCreated(entry);
        cachedEntries.put(key, entry);
        return entry;
    }

    /**
     * Returns the entries using all of the given words, in any order.
     *
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @param  words The words to search. Null elements are ignored.
     * @return Entries using all the given words, or an empty array if none.
     */
    public final synchronized AugmentedEntry[] getEntriesUsingAll(final Alphabet alphabet, final String... words) {
        if (alphabet == null) return WordIndexReader.EMPTY_RESULT;
        return wordIndex[alphabet.ordinal()].getEntriesUsingAll(words);
    }

    /**
     * Returns a collection of entries beginning by the given prefix. If no word begin by
     * the given prefix, then this method will look for shorter character sequences, until
     * a matching characters sequence is found.
     *
     * @param  alphabet Identifies the dictionary index where to search the word.
     * @param  prefix The prefix.
     * @return Entries beginning by the given prefix.
     */
    public final synchronized AugmentedEntry[] getEntriesUsingPrefix(final Alphabet alphabet, final String prefix) {
        if (alphabet == null) return WordIndexReader.EMPTY_RESULT;
        return wordIndex[alphabet.ordinal()].getEntriesUsingPrefix(prefix, new PrefixType(prefix), false);
    }

    /**
     * Searches the best entry matching the given text, or {@code null} if none.
     *
     * @param toSearch       The word to search.
     * @param documentOffset Index of the first character of the given word in the document.
     *        This information is not used by this method. This value is simply stored in the
     *        {@link SearchResult#documentOffset} field for caller convenience.
     * @return The search result, or {@code null} if none.
     */
    public final synchronized SearchResult searchBest(final String toSearch, final int documentOffset) {
        if (toSearch == null || toSearch.isEmpty()) {
            return null;
        }
        final PrefixType pt = new PrefixType(toSearch);
        final AugmentedEntry[] entries = wordIndex[pt.type().alphabet.ordinal()].getEntriesUsingPrefix(toSearch, pt, true);
        Arrays.sort(entries); // Move entries with highest priority first.
        return SearchResult.search(entries, toSearch, pt.type().isKanji, documentOffset);
    }

    /**
     * Invoked by {@link #getEntryAt(int)} when a new entry has been created.
     * The default implementation does nothing. Subclasses can override this
     * method is they need to perform some additional processing on the entry.
     *
     * @param entry The new entry.
     */
    protected void entryCreated(final AugmentedEntry entry) {
    }
}
