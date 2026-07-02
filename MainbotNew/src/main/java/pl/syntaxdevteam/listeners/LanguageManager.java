package pl.syntaxdevteam.listeners;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class LanguageManager {
    private static final Map<String, Properties> locales = new HashMap<>();
    static { loadLanguage("pl"); loadLanguage("eng"); }

    private static void loadLanguage(String langCode) {
        try (InputStream in = LanguageManager.class.getResourceAsStream("/" + langCode + ".properties")) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Properties props = new Properties(); props.load(reader); locales.put(langCode, props);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static String get(String lang, String key, Object... args) {
        if (lang == null || !locales.containsKey(lang)) lang = "eng";
        Properties p = locales.get(lang); if (p == null) return "LANG_ERR";
        String t = p.getProperty(key); if (t == null) return "TEXT_ERR: " + key;
        for (int i = 0; i < args.length; i++) t = t.replace("{" + i + "}", String.valueOf(args[i])).replace("\\n", "\n");
        return t;
    }
}