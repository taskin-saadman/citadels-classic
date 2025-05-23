package citadels.cli;

import java.util.*;

/**
 * Parses the command line input
 */
public final class CommandParser {

    /**
     * Parses the command line string input
     * @param raw command line string input
     * @return Command
     */
    public static Command parse(String raw) {
        raw = raw.trim(); //remove leading and trailing whitespace

        //if the input is empty, return an empty command
        if (raw.isEmpty()) return Command.of("", Collections.emptyList()); 

        List<String> out = new ArrayList<>(); //list to store the parsed command
        StringBuilder builder = new StringBuilder(); //builder to build the current argument

        for (char c : raw.toCharArray()) {
            if (Character.isWhitespace(c)) { //if the current character is a whitespace
                //if the builder is not empty, add current argument to the list then clear the builder
                if (builder.length() > 0) { out.add(builder.toString()); builder.setLength(0); }
            } else builder.append(c); //if the current character is not a whitespace, add it to the builder
        }

        //for the last argument, add it to the list
        if (builder.length() > 0) out.add(builder.toString());

        //key is the first argument in lowercase
        //if the list is empty, return an empty command
        String key = out.isEmpty() ? "" : out.remove(0).toLowerCase();
        return Command.of(key, out);
    }
}