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

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.io.InputStream;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.collections.ObservableList;

import fr.toranomaki.edict.Entry;
import fr.toranomaki.edict.JMdict;
import fr.toranomaki.edict.PartOfSpeech;


/**
 * The text editor, together with the table of word search results on the bottom.
 *
 * @author Martin Desruisseaux
 */
final class Editor {
    /**
     * The editor area.
     */
    private final TextArea text;

    /**
     * The result of word search.
     */
    final WordTable table;

    /**
     * The label to use for showing the selected reading element. This may be Kanji or Hiragana,
     * depending which form is the preferred reading element.
     */
    private final Label reading;

    /**
     * If the {@linkplain #reading} element uses Kanji, the hiragana for that word.
     */
    private final Label hiragana;

    /**
     * The senses for the {@linkplain #reading} element.
     */
    private final VBox senses;

    /**
     * The word element which is currently show.
     */
    private WordElement currentElement;

    /**
     * The cache of flags for each language of interest.
     */
    private final Map<String, Image> flags;

    /**
     * Creates a new instance using the given dictionary for searching words.
     */
    Editor(final JMdict dictionary) {
        flags    = new HashMap<>();
        text     = new TextArea();
        table    = new WordTable(this, dictionary);
        hiragana = new Label();
        reading  = new Label();
        senses   = new VBox();
        hiragana.setAlignment(Pos.BASELINE_CENTER);
        reading .setAlignment(Pos.TOP_CENTER);
        senses  .setAlignment(Pos.TOP_LEFT);
        hiragana.setFont(Font.font(null, 16));
        reading .setFont(Font.font(null, 24));
        hiragana.setMinWidth(160);
        reading .setMinWidth(160);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Control createPane() {
        VBox.setVgrow(reading, Priority.ALWAYS);
        HBox.setHgrow(senses,  Priority.ALWAYS);
        final VBox readingBox = new VBox(); readingBox.getChildren().addAll(hiragana, reading);
        final HBox selectBox  = new HBox(); selectBox .getChildren().addAll(readingBox, senses);
        final SplitPane pane  = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(selectBox, text, table.createPane());
        return pane;
    }

    /**
     * Returns the flag for the given language, or {@code null} if none.
     */
    private Node getFlag(final String language) {
        Image flag = flags.get(language);
        if (flag == null) {
            final InputStream in = Editor.class.getResourceAsStream(language + ".png");
            if (in != null) {
                flag = new Image(in);
                flags.put(language, flag);
            }
        }
        if (flag != null) {
            return new ImageView(flag);
        }
        return null;
    }

    /**
     * Invoked when a new entry has been selected in the {@link WordTable}.
     *
     * @param word The selected entry, or {@code null} if none.
     */
    final void setSelected(final WordElement word) {
        if (word != currentElement) {
            String readingText  = null;
            String hiraganaText = null;
            final ObservableList<Node> sensesList = senses.getChildren();
            sensesList.clear();
            if (word != null) {
                final Entry entry = word.entry;
                hiraganaText = entry.getWord(false, WordElement.WORD_INDEX);
                if ((word.getAnnotationMask(true) & WordElement.PREFERRED_MASK) != 0) {
                    readingText = entry.getWord(true, WordElement.WORD_INDEX);
                }
                if (readingText == null) {
                    readingText = hiraganaText;
                    hiraganaText = null;
                }
                for (final Map.Entry<Set<PartOfSpeech>, Map<Locale, CharSequence>> senses : word.getSenses().entrySet()) {
                    final String partOfSpeech = PartOfSpeech.getDescriptions(senses.getKey());
                    if (partOfSpeech != null) {
                        final Label label = new Label(partOfSpeech);
                        label.setFont(Font.font(null, FontWeight.BOLD, 12));
                        sensesList.add(label);
                    }
                    for (final Map.Entry<Locale, CharSequence> localized : senses.getValue().entrySet()) {
                        final Label label = new Label(localized.getValue().toString(), getFlag(localized.getKey().getLanguage()));
                        label.setWrapText(true);
                        sensesList.add(label);
                    }
                }
            }
            reading .setText(readingText);
            hiragana.setText(hiraganaText);
            currentElement = word;
        }
    }
}
