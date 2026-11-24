package at.dumphey.itemeditor.styler;

/** Tiny string helpers (the project targets Java 8, so no String#isBlank / String#repeat). */
public final class Text {

    private Text() {
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** "fire_aspect" -> "Fire Aspect" */
    public static String title(String key) {
        final String[] parts = key.replace('_', ' ').split(" ");
        final StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static String repeat(char c, int times) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
