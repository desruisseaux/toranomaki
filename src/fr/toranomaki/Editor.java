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
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.io.InputStream;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;

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
     * For size for Latin, Hiragana or Kanji characters.
     */
    private static final int LATIN_SIZE=12, HIRAGANA_SIZE=16, KANJI_SIZE=24;

    /**
     * Identifiers associated to {@linkplain #cachedNodes cached nodes}. Those identifiers are
     * used for distinguish <cite>Part of Speech</cite> and <cite>glossary</cite> from other
     * kind of nodes. The other kind of nodes are flag, which uses the 3 letters language code
     * as their identifier.
     *
     * @see Node#id
     */
    private static final String POS = "POS", GLOSS = "GLOSS";

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
    private final GridPane senses;

    /**
     * The word element which is currently show.
     */
    private WordElement currentElement;

    /**
     * The cache of flags for each language of interest.
     */
    private final Map<String, Image> flags;

    /**
     * The insets for the <cite>Part Of Speech</cite> and the flags.
     * This is used for inserting some spaces between the flags and the text.
     */
    private final Insets posInsets, flagInsets;

    /**
     * A cache of nodes, used when there is frequent addition and removal of elements.
     * This is used for updating the content of the {@link #senses} pane. This cache
     * should never grow very big. It typically contains less than 20 elements.
     */
    private final List<Node> cachedNodes;

    /**
     * Creates a new instance using the given dictionary for searching words.
     */
    Editor(final JMdict dictionary) {
        posInsets   = new Insets(/*top*/ 6, /*right*/ 0, /*bottom*/ 0, /*left*/  6);
        flagInsets  = new Insets(/*top*/ 4, /*right*/ 6, /*bottom*/ 0, /*left*/ 18);
        flags       = new HashMap<>();
        text        = new TextArea();
        table       = new WordTable(this, dictionary);
        hiragana    = new Label();
        reading     = new Label();
        senses      = new GridPane();
        cachedNodes = new ArrayList<>();
        hiragana.setAlignment(Pos.BASELINE_CENTER);
        reading .setAlignment(Pos.TOP_CENTER);
        senses  .setAlignment(Pos.TOP_LEFT);
        hiragana.setFont(Font.font(null, HIRAGANA_SIZE));
        reading .setFont(Font.font(null, KANJI_SIZE));
        hiragana.setMinWidth(160);
        reading .setMinWidth(160);
    }

    /**
     * Creates the widget pane to be shown in the application.
     */
    final Control createPane() {
        final ScrollPane scroll = new ScrollPane();
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setFitToWidth(true); // For allowing labels to wrap lines.
        scroll.setContent(senses);

        final VBox word = new VBox();
        word.setAlignment(Pos.CENTER);
        word.getChildren().addAll(hiragana, reading);

        final BorderPane desc = new BorderPane();
        desc.setLeft(word);
        desc.setCenter(scroll);

        SplitPane.setResizableWithParent(desc, false);
        final SplitPane pane = new SplitPane();
        pane.setOrientation(Orientation.VERTICAL);
        pane.getItems().addAll(desc, text, table.createPane());
        pane.setDividerPositions(0.15, 0.6);
        return pane;
    }

    /**
     * Returns a cached nodes for the given ID. The returned node is removed from the cache.
     */
    private Node getCachedNode(final String id) {
        for (int i=cachedNodes.size(); --i>=0;) {
            final Node node = cachedNodes.get(i);
            if (id.equals(node.getId())) {
                cachedNodes.remove(i);
                return node;
            }
        }
        return null;
    }

    /**
     * Returns the flag for the given language, or {@code null} if none. This method will try to
     * return a cached node if possible. If a new node needs to be created, its layout constraint
     * (except position) will be set. Those constraints are defined in order to simulate the
     * bullets in a list.
     */
    private Node getFlag(final String language) {
        Node node = getCachedNode(language);
        if (node == null) {
            Image flag = flags.get(language);
            if (flag == null) {
                final InputStream in = Editor.class.getResourceAsStream(language + ".png");
                if (in != null) {
                    flag = new Image(in);
                    flags.put(language, flag);
                }
            }
            if (flag != null) {
                node = new ImageView(flag);
                GridPane.setValignment(node, VPos.TOP);
                GridPane.setMargin(node, flagInsets);
                node.setId(language);
            }
        }
        return node;
    }

    /**
     * Invoked when a new entry has been selected in the {@link WordTable}.
     *
     * @param word The selected entry, or {@code null} if none.
     */
    final void setSelected(final WordElement word) {
        if (word != currentElement) {
            final List<Node> children = senses.getChildren();
            cachedNodes.addAll(children);
            children.clear();

            String readingText  = null;
            String hiraganaText = null;
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
                int row = 0;
                for (final Map.Entry<Set<PartOfSpeech>, Map<Locale, CharSequence>> byPos : word.getSenses().entrySet()) {
                    final String partOfSpeech = PartOfSpeech.getDescriptions(byPos.getKey());
                    if (partOfSpeech != null) {
                        Label label = (Label) getCachedNode(POS);
                        if (label != null) {
                            label.setText(partOfSpeech);
                        } else {
                            label = new Label(partOfSpeech);
                            label.setFont(Font.font(null, FontWeight.BOLD, LATIN_SIZE));
                            GridPane.setMargin(label, posInsets);
                            GridPane.setColumnSpan(label, 2);
                            label.setId(POS);
                        }
                        senses.add(label, 0, row++);
                    }
                    for (final Map.Entry<Locale, CharSequence> localized : byPos.getValue().entrySet()) {
                        final Node   flag = getFlag(localized.getKey().getISO3Language());
                        final String text = localized.getValue().toString();
                        Label label = (Label) getCachedNode(GLOSS);
                        if (label != null) {
                            label.setText(text);
                        } else {
                            label = new Label(text);
                            label.setWrapText(true);
                            GridPane.setHgrow(label, Priority.ALWAYS);
                            label.setId(GLOSS);
                        }
                        senses.add(flag,  0, row);
                        senses.add(label, 1, row++);
                    }
                }
            }
            reading .setText(readingText);
            hiragana.setText(hiraganaText);
            currentElement = word;
        }
    }
}
