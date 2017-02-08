package index;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.solr.common.SolrInputDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Copyright 2016 Kunal Sheth
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class IndexManager {

    private static final int fieldLength = 5000;

    public static Document parse(String url, InputStream input) {
        try {
            return Jsoup.parse(input, null, url);
        } catch (IOException | IllegalArgumentException e) {
            System.err.print("\n" + e.getMessage() + "\n");
            return null;
        }
    }

    public static Triple<SolrInputDocument, Collection<String>, Collection<String>> index(Document document) {
        final SolrInputDocument index = new SolrInputDocument();
        index.setField("id", document.location());
        index.setField("time", String.valueOf(System.currentTimeMillis()));
        index.setField("title", document.title());

        final Set<String> links =
                document.select("a[href]").stream()
                        .map(e -> e.attr("abs:href"))
                        .collect(Collectors.toSet());
        final Set<String> media =
                document.select("[src]").stream()
                        .map(e -> e.attr("abs:src"))
                        .collect(Collectors.toSet());

        links.forEach(link -> index.addField("link", link));
        media.forEach(link -> index.addField("media", link));

        formatText(document.getElementsByTag("h1").stream())
                .forEach(e -> index.addField("h1", e));

        formatText(document.getElementsByTag("h2").stream())
                .forEach(e -> index.addField("h2", e));

        formatText(document.getElementsByTag("h3").stream())
                .forEach(e -> index.addField("h3", e));

        formatText(document.getElementsByTag("strong").stream())
                .forEach(e -> index.addField("strong", e));

        formatText(document.getElementsByTag("em").stream())
                .forEach(e -> index.addField("em", e));

        formatText(document.getElementsByTag("b").stream())
                .forEach(e -> index.addField("b", e));

        formatText(document.getElementsByTag("u").stream())
                .forEach(e -> index.addField("u", e));

        formatText(document.getElementsByTag("i").stream())
                .forEach(e -> index.addField("i", e));

        int i = 0;
        Collection<String> text = chunkToLength(document.text());
        for (String chunk : text) index.addField(++i + "_text", chunk);

        return Triple.of(index, links, media);
    }

    private static Stream<String> formatText(final Stream<Element> elements) {
        return elements.map(Element::text)
                .map(string -> string.replaceAll("\\s+", " "))
                .map(String::trim)
                .filter(string -> !(string.length() < 3))
                .map(IndexManager::truncateToLength);
    }

    private static List<String> chunkToLength(String text) {
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() < fieldLength) return Collections.singletonList(text);

        int i = 0;
        List<String> chunks = new LinkedList<>();
        while (true) {
            int hardCutPoint = Math.min(text.length(), i + fieldLength);
            int softCutPoint = text.lastIndexOf(' ', hardCutPoint);
            int endIndex = (softCutPoint > i && hardCutPoint != text.length()) ? softCutPoint : hardCutPoint;

            chunks.add(truncateToLength(text.substring(i, endIndex)).trim());

            if (endIndex == text.length()) break;
            i = endIndex;
        }
        return chunks;
    }

    private static String truncateToLength(String text) {
        if (text.length() <= fieldLength) return text;
        return text.substring(0, fieldLength);
    }
}
