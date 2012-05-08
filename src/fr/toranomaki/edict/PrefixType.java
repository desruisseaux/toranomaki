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

import fr.toranomaki.grammar.CharacterType;


/**
 * A modifiable container for {@link CharacterType} enum.
 */
final class PrefixType {
    /**
     * The type of the prefix.
     */
    private CharacterType type;

    /**
     * Ensures that {@code PrefixType} is initialized to a non-null value.
     */
    PrefixType(final String prefix) {
        type = CharacterType.forWord(prefix);
    }

    /**
     * Update this {@code PrefixType} to a new value.
     */
    void update(final String prefix) {
        final CharacterType candidate = CharacterType.forWord(prefix);
        if (candidate != null) {
            type = candidate;
        }
    }

    /**
     * Returns the character type.
     */
    CharacterType type() {
        return type;
    }

    /**
     * {@code true} if the character type is alphabetic.
     */
    boolean isAlphabetic() {
        return type == CharacterType.ALPHABETIC;
    }
}
