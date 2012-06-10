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

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;



/**
 * Reports recoverable warnings and other messages.
 *
 * @author Martin Desruisseaux
 */
public final class Logging {
    /**
     * The application-wide logger.
     */
    public static final Logger LOGGER = Logger.getLogger("fr.toranomaki");

    /**
     * Do not allow (for now) instantiation of this class.
     */
    private Logging() {
    }

    /**
     * Invoked when a recoverable error occurred. This method should be invoked for recoverable
     * error only, i.e. when the caller can fallback on a reasonable alternative. Fatal exceptions
     * shall be propagated instead.
     *
     * @param classe  The class where the error occurred.
     * @param method  The method name where the error occurred.
     * @param error   The error.
     */
    public static void recoverableException(final Class<?> classe, final String method, final Throwable error) {
        final LogRecord record = new LogRecord(Level.WARNING, error.toString());
        record.setSourceClassName(classe.getName());
        record.setSourceMethodName(method);
        record.setThrown(error);
        record.setLoggerName(LOGGER.getName());
        LOGGER.log(record);
    }

    /**
     * Invoked when a serious error occurred which may result in lost of data.
     *
     * @param classe  The class where the error occurred.
     * @param method  The method name where the error occurred.
     * @param message The error.
     */
    public static void possibleDataLost(final Class<?> classe, final String method, final String message) {
        final LogRecord record = new LogRecord(Level.WARNING, message);
        record.setSourceClassName(classe.getName());
        record.setSourceMethodName(method);
        record.setLoggerName(LOGGER.getName());
        LOGGER.log(record);
    }

    /**
     * Invoked when a serious error occurred which may result in lost of data.
     *
     * @param error The error.
     */
    public static void possibleDataLost(final Throwable error) {
        error.printStackTrace(); // TODO: report to the user.
    }
}
