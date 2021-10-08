import java.io.*;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * The main class for the bank. The bank must constantly listen for
 * input from both the command line and the router.
 */

public class Bank {

	private final static String prompt = "Bank: ";
	public static HashMap<String, BankAccount> accounts;
    
    public static void main(String[] args) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {

        if (args.length != 1) {
            System.out.println("Usage: java Bank <Bank-port>");
            System.exit(1);
        }

        int bankPort = Integer.parseInt(args[0]);
        
        // Create the three users' bank accounts
        accounts = new HashMap<String, BankAccount>();
        accounts.put("Alice", new BankAccount("Alice", 100, "0", "0"));
        accounts.put("Bob", new BankAccount("Bob", 100, "0", "0"));
        accounts.put("Carol", new BankAccount("Carol", 0, "0", "0"));
        
        try {
            /* Connect to port */
            Socket socket = new Socket("localhost", bankPort);
            final BankProtocol bankProtocol = new BankProtocol(socket.getInputStream(), socket.getOutputStream());

            /* Handle command-line input */
            Thread local = new Thread() {
                public void run() {
                    System.out.print(prompt);
                    BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

                    try{
                        bankProtocol.processLocalCommands(stdIn, prompt);
                    } catch (IOException e) {
                        System.out.println("Failed to process user input.");
                        System.exit(0);
                    }
                }
            };

            /* Handle router input */
            Thread remote = new Thread() {
                public void run() {
                    try {
                        bankProtocol.processRemoteCommands();
                    } catch (IOException e) {
                        System.out.println("Failed to process remote input.");
                        System.exit(0);
                    }
                }
            };

            local.start();
            remote.start();

            try {
                local.join();
                remote.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            /* Clean up */
            try {
                bankProtocol.close();
                socket.close();
            } catch(IOException e) {
                System.out.println("Could not close socket.");
                System.exit(0);
            }

        } catch (IOException e) {
            System.out.println("Failed to connect to bank on port " + bankPort + ". Please try a different port.");
            System.exit(0);
        }
    }
}
