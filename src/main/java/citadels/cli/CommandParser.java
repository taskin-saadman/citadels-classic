package citadels.cli;

import java.util.*;

/** Very lenient split on whitespace – keeps quoted substrings intact. */
//purpose: parse the command line input
public final class CommandParser {

    public static Command parse(String raw) {
        raw = raw.trim();
        if (raw.isEmpty()) return Command.of("", Collections.emptyList()); 

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
