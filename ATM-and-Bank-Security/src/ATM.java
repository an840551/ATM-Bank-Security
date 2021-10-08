import java.io.*;
import java.net.Socket;

/**
 * The main class for the ATM.
 */

public class ATM {

    private static final String prompt = "ATM: ";

    public static void main(String[] args) {

        if(args.length != 1) {
            System.out.println("Usage: java ATM <ATM-port>");
            System.exit(1);
        }

        int atmPort = Integer.parseInt(args[0]);

        try {
            /* Connect to port */
            Socket socket = new Socket("localhost", atmPort);
            Protocol atmProtocol = new ATMProtocol(socket.getInputStream(), socket.getOutputStream());

            /* Handle command-line input */
            System.out.print(prompt);
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

            try {
                atmProtocol.processLocalCommands(stdIn, prompt);
            } catch (IOException e) {
                System.out.println("Failed to process local input.");
                System.exit(0);
            }

            /* Clean up */
            try {
                atmProtocol.close();
                socket.close();
            } catch (IOException e) {
                System.out.println("Could not close socket.");
                System.exit(0);
            }

        } catch (IOException e) {
            System.out.println("Could not connect to ATM on port " + atmPort + ". Please try a different port.");
            System.exit(0);
        }
    }

}

