// src/main/java/citadels/cli/CommandParser.java
package citadels.cli;

import java.util.*;

public final class CommandParser {

    private CommandParser() {}  // utility

    /**
     * Very lenient split on whitespace; keeps quoted strings intact.
     */
    public static Command parse(String raw) {
        raw = raw.trim();
        if (raw.isEmpty()) return Command.of("", List.of());

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : raw.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());

        String keyword = tokens.isEmpty() ? "" : tokens.remove(0);
        return Command.of(keyword, tokens);
    }
}
