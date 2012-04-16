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

import java.io.IOException;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;


/**
 * Base class of every classes for reading or writing the dictionary file in its binary format,
 * or part of that file. The binary format is specific to this project and is documented in the
 * {@link fr.toranomaki.edict.writer} package.
 *
 * @author Martin Desruisseaux
 */
public abstract class DictionaryFile {
    /**
     * Arbitrary magic number. The value on the right side of {@code +} is the version number,
     * to be incremented every time we apply an incompatible change in the file format.
     */
    protected static final int MAGIC_NUMBER = 810241902 + 5;

    /**
     * The byte order used in the dictionary files.
     * This is fixed to the byte order used by Intel processors for now.
     */
    protected static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    /**
     * The encoding used for Latin or Japanese characters. Those encodings are used only
     * in the header of the binary file. After the header, we will use our own encoding
     * which will replace the most frequent character sequence by a code.
     */
    protected static final String LATIN_ENCODING = "UTF-8",
                                  JAPAN_ENCODING = "UTF-16";

    /**
     * The mask indicating that a character is encoded on two bytes rather than one.
     * We store the 128 most common characters on 1 byte, and the remaining on two
     * bytes.
     */
    protected static final int MASK_CHARACTER_INDEX_ON_TWO_BYTES = 0x80;

    /**
     * Number of bits to use for storing the word length in a {@code int} reference value.
     * The remaining number of bits will be used for storing the word start position.
     */
    protected static final int NUM_BITS_FOR_WORD_LENGTH = 9;

    /**
     * Number of bits to use for storing the count of Kanji and reading elements in an entry.
     */
    protected static final int NUM_BITS_FOR_ELEMENT_COUNT = 4;

    /**
     * Size in bytes of one index value in the index array, which is the size of the {@code int} type.
     *
     * <p>Note: if this value is changed, we recommend to perform a search for {@code Integer.SIZE}
     * on the code base, especially in {@code WordIndexReader} and {@code WordIndexWriter}.</p>
     */
    protected static final int NUM_BYTES_FOR_INDEX_ELEMENT = Integer.SIZE / Byte.SIZE;

    /**
     * Number of bytes to use for storing the position of an entry.
     */
    protected static final int NUM_BYTES_FOR_ENTRY_POSITION = 3;

    /**
     * For subclass constructors only.
     */
    protected DictionaryFile() {
    }

    /**
     * Returns the dictionary installation directory. This method returns the first of the
     * following choices:
     * <p>
     * <ul>
     *   <li>If the {@code "toranomaki.dir"} property is set, returns that directory.</li>
     *   <li>Otherwise if this application is bundled in a JAR file, returns the directory
     *       that contain the JAR file.</li>
     *   <li>Otherwise returns the user directory.</li>
     * </ul>
     * <p>
     * If every cases, this method verify that the directory exists before to return it.
     *
     * @return The application directory.
     * @throws IOException In an error occurred while getting the application directory.
     */
    public static Path getDirectory() throws IOException {
        Path directory;
        final String property = System.getProperty("toranomaki.dir");
        if (property != null) {
            directory = Paths.get(property);
        } else {
            URL url = DictionaryFile.class.getResource("DictionaryFile.class");
            if ("jar".equals(url.getProtocol())) {
                String path = url.getPath();
                path = path.substring(0, path.indexOf('!'));
                url = new URL(path);
                try {
                    directory = Paths.get(url.toURI());
                } catch (URISyntaxException e) {
                    throw new MalformedURLException(e.getLocalizedMessage());
                }
                directory = directory.getParent();
            } else {
                directory = Paths.get(System.getProperty("user.dir", "."));
            }
        }
        if (!Files.isDirectory(directory)) {
            throw new FileNotFoundException(directory.toString());
        }
        return directory;
    }

    /**
     * {@code true} if we are reading or writing a dictionary part for the Japanese language,
     * or {@code false} for the senses in westerner languages. This trivial method is mostly
     * a placeholder for identifying the code to update if we want to support more languages
     * in a future version.
     *
     * @param  index The index of the dictionary parts being read or written.
     * @return The language, currently as a boolean but may be changed in a future version.
     */
    protected static boolean getLanguageAt(final int index) {
        switch (index) {
            case 0: return true;
            case 1: return false;
            default: throw new IndexOutOfBoundsException(String.valueOf(index));
        }
    }

    /**
     * The converse of {@link #getLanguageAt(int)}. The argument may be changed from a boolean
     * to something else in a future version.
     *
     * @param isJapanese {@code true} for the Japanese language, or {@code false} for senses.
     * @return The index of the given language.
     */
    protected static int getLanguageIndex(final boolean isJapanese) {
        return isJapanese ? 0 : 1;
    }

    /**
     * Reads bytes from the given channel until the given buffer is full, then flips the buffer.
     */
    static void readFully(final ReadableByteChannel in, final ByteBuffer buffer) throws IOException {
        do if (in.read(buffer) < 0) {
            throw new EOFException();
        } while (buffer.hasRemaining());
        buffer.flip();
    }
}
