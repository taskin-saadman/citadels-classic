package citadels.cli;

import java.util.*;

/** Very lenient split on whitespace – keeps quoted substrings intact. */
public final class CommandParser {

    private CommandParser() {}

    public static Command parse(String raw) {
        raw = raw.trim();
        if (raw.isEmpty()) return Command.of("", List.of());

        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQ = false;

        for (char c : raw.toCharArray()) {
            if (c == '"') {
                inQ = !inQ;
            } else if (Character.isWhitespace(c) && !inQ) {
                if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
            } else sb.append(c);
        }
        if (sb.length() > 0) out.add(sb.toString());

        String key = out.isEmpty() ? "" : out.remove(0).toLowerCase();
        return Command.of(key, out);
    }
}
