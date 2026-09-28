package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;

public class LocalizationResourcesTest {
    private static final Path RES = Path.of("src/main/res");
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([bBhHsScCdoxXeEfgGaA])");

    @Test public void declaredLanguagesHaveCompleteResourcesAndMatchingArguments() throws Exception {
        Map<String, Element> source = resources(RES.resolve("values"));
        List<String> tags = new ArrayList<>();
        for (Element locale : children(parse(RES.resolve("xml/locales_config.xml")))) {
            String tag = locale.getAttributeNS(ANDROID, "name");
            tags.add(tag);
            if (tag.equals("en")) continue;
            String qualifier = tag.contains("-") ? "b+" + tag.replace('-', '+') : tag;
            Map<String, Element> translated = resources(RES.resolve("values-" + qualifier));
            assertEquals(tag, source.keySet(), translated.keySet());
            for (var item : source.entrySet()) {
                String name = tag + ":" + item.getKey();
                Element original = item.getValue();
                Element local = translated.get(item.getKey());
                assertEquals(name, original.getTagName(), local.getTagName());
                if (original.getTagName().equals("string")) {
                    sameArguments(name, original, local);
                } else if (original.getTagName().equals("string-array")) {
                    List<Element> originalItems = children(original), localItems = children(local);
                    assertEquals(name, originalItems.size(), localItems.size());
                    for (int i = 0; i < originalItems.size(); i++) {
                        sameArguments(name + ":" + i, originalItems.get(i), localItems.get(i));
                    }
                } else {
                    var originalItems = quantities(original);
                    var localItems = quantities(local);
                    assertTrue(name, localItems.containsKey("other"));
                    if (tag.equals("ru")) assertTrue(name, localItems.keySet().containsAll(Set.of("one", "few", "many", "other")));
                    if (Set.of("es", "fr", "pt-BR").contains(tag)) assertTrue(name, localItems.keySet().containsAll(Set.of("one", "many", "other")));
                    for (var form : localItems.entrySet()) {
                        assertTrue(name, Set.of("zero", "one", "two", "few", "many", "other").contains(form.getKey()));
                        sameArguments(name + ":" + form.getKey(), originalItems.getOrDefault(form.getKey(), originalItems.get("other")), form.getValue());
                    }
                }
            }
        }
        assertEquals(List.of("en", "ru", "zh-Hans", "es", "de", "ja", "pt-BR", "fr"), tags);
    }

    @Test public void androidOwnsLanguageConfigurationAndActivityRecreation() throws Exception {
        Element application = (Element) parse(Path.of("src/main/AndroidManifest.xml"))
                .getElementsByTagName("application").item(0);
        assertEquals("@xml/locales_config", application.getAttributeNS(ANDROID, "localeConfig"));
        var activities = application.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            assertFalse(activity.getAttributeNS(ANDROID, "name"),
                    List.of(activity.getAttributeNS(ANDROID, "configChanges").split("\\|")).contains("locale"));
        }
    }

    private static Map<String, Element> resources(Path directory) throws Exception {
        Map<String, Element> result = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".xml")).sorted().toList()) {
                for (Element entry : children(parse(file))) {
                    if (!Set.of("string", "string-array", "plurals").contains(entry.getTagName())
                            || entry.getAttribute("translatable").equals("false")) continue;
                    assertNull(file + ": duplicate " + entry.getAttribute("name"), result.put(entry.getAttribute("name"), entry));
                }
            }
        }
        return result;
    }

    private static Map<String, Element> quantities(Element parent) {
        Map<String, Element> result = new LinkedHashMap<>();
        for (Element entry : children(parent)) {
            assertNull(parent.getAttribute("name"), result.put(entry.getAttribute("quantity"), entry));
        }
        return result;
    }

    private static void sameArguments(String name, Element source, Element translated) {
        assertFalse(name, translated.getTextContent().isBlank());
        assertEquals(name, source.getAttribute("formatted"), translated.getAttribute("formatted"));
        if (!source.getAttribute("formatted").equals("false")) {
            assertEquals(name, arguments(source.getTextContent()), arguments(translated.getTextContent()));
        }
    }

    private static List<String> arguments(String value) {
        List<String> result = new ArrayList<>();
        var matcher = FORMAT.matcher(value);
        int implicit = 0;
        while (matcher.find()) {
            String index = matcher.group(1) == null ? Integer.toString(++implicit) : matcher.group(1);
            result.add(index + ":" + matcher.group(2));
        }
        Collections.sort(result);
        return result;
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) result.add(element);
        }
        return result;
    }

    private static Element parse(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }
}
