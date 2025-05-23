package citadels.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Immutable representation (final) of a user command plus positional arguments.
 * A command has 2 parts: a keyword and a list of arguments.
 */
public final class Command {

    private final String keyword; //build, t, end, etc.
    private final List<String> args; //arguments (e.g. 1, 2, 3)

    /**
     * Constructor for Command
     * @param keyword keyword of the command
     * @param args arguments of the command
     */
    private Command(String keyword, List<String> args) {
        this.keyword = keyword;
        this.args = args;
    }

    /**
     * Factory method used by {@link CommandParser}.
     * @param keyword keyword of the command
     * @param args arguments of the command
     * @return Command
     */
    static Command of(String keyword, List<String> args) {
        return new Command(keyword.toLowerCase(), args);
    }

    /**
     * Getter for the keyword
     * @return keyword
     */
    public String keyword() { return keyword; }

    /**
     * Getter for the arguments
     * @return arguments
     */
    public List<String> args() { return args;}

    /**
     * Getter for the argument at index i
     * @param i index of the argument
     * @param defaultVal default value if the index is out of bounds
     * @return argument at index i
     */
    public String arg(int i, String defaultVal) {
        return (i < args.size()) ? args.get(i) : defaultVal;
    }

    /**
     * toString method
     * @return string representation of the command
     */
    @Override
    public String toString() {
        return keyword + " " + String.join(" ", args);
    }
}
