package net.maidens.maiden.social;

import net.minecraft.util.RandomSource;

/** Things any maiden might say, and the names they're given. */
public final class Dialogue {
    private Dialogue() {}

    public static final String[] NAMES = {
            "Aria", "Elise", "Mira", "Lena", "Rosalind", "Ivy", "Clara", "Freya", "Maren", "Selene", "Tamsin", "Wren",
            "Odette", "Brielle", "Liora", "Nell", "Sorrel", "Juniper", "Elowen", "Hazel", "Isolde", "Fern", "Margot", "Celia",
            "Linnea", "Ada", "Briar", "Coralie", "Delphine", "Esme", "Gwen", "Ilse", "Kaia", "Lark", "Marigold", "Nora",
            "Ophelia", "Poppy", "Rhea", "Sylvie"};

    public static final String[] TIRED = {"I think that's enough of that for today, %s.", "We've done that a lot today. Tomorrow?"};
    public static final String[] MARRIED_ELSEWHERE = {"I'm a married woman, %s!", "My heart belongs to someone else."};
    public static final String[] SPOUSE = {"You're so silly, my love.", "Come here, you.", "I'm so glad I married you, %s."};

    public static String pick(String[] lines, RandomSource random, String name) {
        return String.format(lines[random.nextInt(lines.length)], name);
    }
}
