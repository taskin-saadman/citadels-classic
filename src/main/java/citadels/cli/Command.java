// src/main/java/citadels/cli/Command.java
package citadels.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Immutable representation of a user command plus positional arguments.
 */
public final class Command {

    private final String keyword;
    private final List<String> args;

    private Command(String keyword, List<String> args) {
        this.keyword = keyword;
        this.args = args;
    }

    /** Factory method used by {@link CommandParser}. */
    static Command of(String keyword, List<String> args) {
        return new Command(keyword.toLowerCase(), args);
    }

    public String keyword()     { return keyword; }
    public List<String> args()  { return args;    }

    public String arg(int i, String defaultVal) {
        return (i < args.size()) ? args.get(i) : defaultVal;
    }

    @Override
    public String toString() {
        return keyword + " " + String.join(" ", args);
    }
}
