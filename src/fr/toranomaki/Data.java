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
package fr.toranomaki;

import java.net.URL;
import java.net.URISyntaxException;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Base class of everything which are going to load or save data on/to the disk.
 *
 * @author Martin Desruisseaux
 */
public abstract class Data {
    /**
     * The encoding of the file to be saved.
     */
    static final String FILE_ENCODING = "UTF-8";

    /**
     * The <cite>byte order mark</cite> used to signal endianness of UTF-8 text files. This mark
     * is also used for indicating to softwares (Notepad on Windows, TextEdit of MacOS) that the
     * file is encoded in UTF-8.
     * <p>
     * The Unicode value is {@value}. The corresponding bytes sequence is
     * {@code 0xEF}, {@code 0xBB}, {@code 0xBF}.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Byte_order_mark">Byte order mark on Wikipedia</a>
     */
    static final char BYTE_ORDER_MARK = '\uFEFF';

    /**
     * For subclasses constructor only.
     */
    protected Data() {
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
            URL url = Data.class.getResource("DataStore.class");
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
}
