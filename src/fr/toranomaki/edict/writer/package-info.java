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


/**
 * Converts the <a href="http://www.csse.monash.edu.au/~jwb/edict.html">EDICT</a> XML file
 * to the binary format used by the {@link fr.toranomaki.edict} package. This package needs
 * to be used only on the developer machine; it can be excluded from the client applications.
 * <p>
 * The file format is:
 * <p>
 * <ul>
 *   <li>For each index (currently only two: Japanese words and senses):
 *     <ul>
 *       <li>The {@linkplain #MAGIC_NUMBER magic number} as a {@code int}.</li>
 *       <li>Number of words, as an {@code int}.</li>
 *       <li>The length of the pool of bytes, as an {@code int}.</li>
 *       <li>Length of the character sequences pool, as an {@code int}.</li>
 *       <li>Number of character sequences used by the encoding, as an unsigned {@code short}.</li>
 *       <li>For each character sequence:
 *         <ul>
 *           <li>Position and length of each character sequence, packed in {@code int}.</li>
 *         </ul>
 *       </li>
 *       <li>All character sequences encoded in UTF-8 or UTF-16.</li>
 *     </ul>
 *   </li>
 *   <li>For each index (again):
 *     <ul>
 *       <li>Packed references to the encoded words as {@code int} numbers where the first bits are
 *           the index of the first byte to use in the pool (0 is the first byte after all packed
 *           references), and the last {@code NUM_BITS_FOR_WORD_LENGTH} bits are the number of bytes
 *           to read from the pool.</li>
 *       <li>A pool of bytes which represent the encoded words.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author Martin Desruisseaux
 */
package fr.toranomaki.edict.writer;
