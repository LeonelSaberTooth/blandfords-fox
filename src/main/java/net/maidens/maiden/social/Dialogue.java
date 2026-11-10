package net.maidens.maiden.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.util.RandomSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Things any maiden might say (data/maidens/dialogue/common.json), the names they're given, and reading line files. */
public final class Dialogue {
    private Dialogue() {}

    public static final String[] NAMES = {
            "Aria", "Elise", "Mira", "Lena", "Rosalind", "Ivy", "Clara", "Freya", "Maren", "Selene", "Tamsin", "Wren",
            "Odette", "Brielle", "Liora", "Nell", "Sorrel", "Juniper", "Elowen", "Hazel", "Isolde", "Fern", "Margot", "Celia",
            "Linnea", "Ada", "Briar", "Coralie", "Delphine", "Esme", "Gwen", "Ilse", "Kaia", "Lark", "Marigold", "Nora",
            "Ophelia", "Poppy", "Rhea", "Sylvie"};

    public static final String[] MALE_NAMES = {
            "Aldric", "Bram", "Cedric", "Dorian", "Edmund", "Finn", "Gareth", "Hugo", "Ivo", "Jasper", "Kian", "Leif",
            "Milo", "Niall", "Osric", "Percival", "Quentin", "Rowan", "Silas", "Tobin", "Ulric", "Victor", "Wendel", "Alaric",
            "Bastian", "Corin", "Darian", "Emrys", "Florian", "Gideon", "Hollis", "Lucan", "Merrick", "Oren", "Pell", "Torin"};

    private static final Map<String, String[]> COMMON = load("common");
    public static final String[] TIRED = COMMON.get("tired");
    public static final String[] MARRIED_ELSEWHERE = COMMON.get("married_elsewhere");
    public static final String[] SPOUSE = COMMON.get("spouse");

    /**
     * Reads a file of lines from the mod's resources, data/maidens/dialogue/&lt;name&gt;.json: an object of
     * line lists ("chat_good": ["...", ...]). A list that's missing comes back as one "..." line.
     */
    public static Map<String, String[]> load(String name) {
        Map<String, String[]> out = new HashMap<>() {
            @Override
            public String[] get(Object key) {
                String[] v = super.get(key);
                return v == null || v.length == 0 ? new String[]{"..."} : v;
            }
        };
        String path = "/data/maidens/dialogue/" + name + ".json";
        try (InputStream in = Dialogue.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                List<String> lines = new ArrayList<>();
                for (JsonElement l : e.getValue().getAsJsonArray()) lines.add(l.getAsString());
                out.put(e.getKey(), lines.toArray(new String[0]));
            }
        } catch (Exception e) {
            LogUtils.getLogger().error("Maidens: couldn't read dialogue {}", path, e);
        }
        return out;
    }

    public static String pick(String[] lines, RandomSource random, String name) {
        return String.format(lines[random.nextInt(lines.length)], name);
    }
}
