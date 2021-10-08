import java.io.BufferedReader;
import java.io.IOException;

/**
 * A protocol object handles local input and allows for easy clean-up.
 */

public interface Protocol {

    void processLocalCommands(BufferedReader stdIn, String prompt) throws IOException;
    void close() throws IOException;

}
