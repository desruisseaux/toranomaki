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

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;


/**
 * Base class of process that may take a while to execute. This base class manage a reference
 * to a progress and a label property.
 *
 * @author Martin Desruisseaux
 */
abstract class LengthyProcess {
    /**
     * Progress indicator, ranging from 0 to 1 inclusive. This is typically set to the value of
     * {@link javafx.scene.control.ProgressIndicator#progressProperty()}.
     */
    private final DoubleProperty progress;

    /**
     * A description of the operation in progress. This is typically set to the value of
     * {@link javafx.scene.control.Labeled#textProperty()}.
     */
    private final StringProperty progressLabel;

    /**
     * When was reported the last progress. Used in order to avoid too many
     * {@inkplain #progress} updates.
     */
    private transient long lastProgressTime;

    /**
     * Creates a new process.
     *
     * @param progressLabel Where to write a description of the operation in progress.
     * @param progress      Where to write progress indicator, ranging from 0 to 1 inclusive.
     */
    protected LengthyProcess(final StringProperty progressLabel, final DoubleProperty progress) {
        this.progressLabel = progressLabel;
        this.progress      = progress;
        lastProgressTime   = System.nanoTime();
    }

    /**
     * Sets the label for the progress.
     */
    protected final void setProgressLabel(final String description) {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                progressLabel.setValue(description);
                progress.setValue(0);
            }
        });
    }

    /**
     * Reports progress.
     *
     * @param index An arbitrary integer value.
     * @param scale A scale factory by which to multiply the integer value in order to get a
     *        number in the [0 .. 1] range.
     */
    protected final void progress(final int index, final double scale) {
        final long time = System.nanoTime();
        if (time - lastProgressTime >= 250000000) { // 0.25 seconds
            lastProgressTime = time;
            final double value = index * scale;
            Platform.runLater(new Runnable() {
                @Override public void run() {
                    progress.setValue(value);
                }
            });
        }
    }
}
