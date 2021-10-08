import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * An ATMProtocol processes local commands sent to the ATM and writes to or reads
 * from the router as necessary. You can use whatever method you would like to
 * read from and write to the router, but this is an example to get you started.
 */

public class ATMProtocol implements Protocol {

    private PrintWriter writer;
    private BufferedReader reader;
    
    // Used to keep track of the user's session
    private String loggedInUser = null;
    
    // Private keys. Same two are saved in the BankProtocol class. Zeroed out.
    private byte[] aesKeyRaw = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    private byte[] macKeyRaw = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    
    // Object that aids in the encryption, decryption, and MACing necessary for message transmission
    private EncryptionHelper encrypter;
    
    // Helps to prevent replay attacks
    private Long lastResponseTime = new Long(0);

    public ATMProtocol(InputStream inputStream, OutputStream outputStream) {
        writer = new PrintWriter(outputStream, true);
        reader = new BufferedReader(new InputStreamReader(inputStream));
        encrypter = new EncryptionHelper(aesKeyRaw, macKeyRaw);
    }

    /* Continue to read input until terminated. */
    public void processLocalCommands(BufferedReader stdIn, String prompt) throws IOException {
        String userInput;

        while((userInput = stdIn.readLine()) != null) {
            try {
				processCommand(userInput, stdIn);
			} catch (Exception e) {
				e.printStackTrace();
			}
            System.out.print(prompt);
        }

        stdIn.close();
    }

    /* Interpret a command sent to the ATM and print the result to the output stream. */
    private void processCommand(String command, BufferedReader stdIn) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
    	// Necessary variables
    	String pin, userInput;
    	
    	// Break the command up into parts that we can use
    	String[] parts = command.split(" ");
    	String action = parts[0];
    	
    	// Check for begin-session or output errors
    	if(action.equals("begin-session")) {
    		// Make sure the user is trying to log in with a username
    		if(parts.length != 2) {
    			System.out.println();
    			return;
    		}
    		
    		// Prompt for the user's pin
    		System.out.print("PIN? ");
    		pin = stdIn.readLine();
    		
    		// Make sure the entered pin is numeric and 4 digits
    		try {
    			Integer.parseInt(pin);
    			if(pin.length() != 4)
    				throw new NumberFormatException();
    		} catch (NumberFormatException e) {
    			System.out.println("unathorized\n");
    			return;
    		}
    		
    		// Read card in preparation for authorization
    		String userCandidate = parts[1], cardSecret;
    		try {
        		BufferedReader card = new BufferedReader(new FileReader(parts[1] + ".card"));
    			cardSecret = card.readLine();
    		} catch (Exception e) {
    			System.out.println("unauthorized\n");
    			return;
    		}
    		String message = "AUTH:" + userCandidate + ":" + pin + ":" + cardSecret;
    		String response = "";
    		
    		// Send authorization message to the Bank
    		try {
    			writer.println(encrypter.secureMessage(message));
    			response = readValidLine();
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    		
    		if(encrypter.revealMessage(response).equals("AUTH:" + userCandidate + ":PASS")) {
	    		// Pass the user to the commands that can only be executed when logged in
	    		loggedInUser = userCandidate;
				System.out.print("authorized\n\nATM (" + loggedInUser + "): ");
	    		while(loggedInUser != null && (userInput = stdIn.readLine()) != null) {
	    			processInSessionCommands(userInput);
	    			if(loggedInUser != null)
	    				System.out.print("ATM (" + loggedInUser + "): ");
	    		}
    		} else {
    			System.out.print("unauthorized\n");
    		}

    	} else if(action.equals("balance") || action.equals("withdraw") || action.equals("end-session")) {
    		// Trying to do an in-session command while logged out
			System.out.println("no user logged in\n");
			return;
			
    	}    	
    	
    	// Extra space for extra clarity.
    	System.out.println();
    }
    
    private void processInSessionCommands(String command) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, IOException {
    	// Break the command up into parts that we can use
    	String[] parts = command.split(" ");
    	String action = parts[0], response;
    	
    	if(action.equals("end-session")) {
		if(parts.length != 1) {
			System.out.println();
			return;
		}
    		System.out.println("user logged out");
    		loggedInUser = null;
    		return;
    	} else if (action.equals("balance")) {
		if(parts.length != 1) {
			System.out.println();
			return;
		}
        	writer.println(encrypter.secureMessage("BALANCE:" + loggedInUser));
    		response = encrypter.revealMessage(readValidLine());
    		if(!responseValid(response)) return;    		
    		System.out.println("$" + response.substring(response.lastIndexOf(":") + 1));
    		
    	} else if (action.equals("withdraw")) {
    		if(parts.length != 2) {
    			System.out.println();
    			return;
    		}
    		String amount = parts[1];
    		writer.println(encrypter.secureMessage("WITHDRAW:" + loggedInUser + ":" + amount));
    		response = encrypter.revealMessage(readValidLine());
    		if(!responseValid(response)) return;
    		String outcome = response.substring(response.lastIndexOf(":") + 1);
    		if(outcome.equals("SUCCEED")) {
    			System.out.println("$" + amount + " dispensed");
    		} else if (outcome.equals("INSUFFICIENT")){
    			System.out.println("insufficient funds");
    		}
    		
    	}

    	// Extra space for extra clarity.
    	System.out.println();
    }
    
    /**
     * Rather than just using the line reader.readLine() when waiting for a response
     * from the Bank, this function makes sure that the line being processed is valid.
     * This function prevents the Router from sending double messages back from the Bank.
     * That is, the router cannot intercept a message from the Bank allowing a withdrawl of
     * money and repeat it an arbitrary amount of time.
     * 
     * @return
     * @throws IOException
     */
    private String readValidLine() throws IOException {
    	String candidate;
    	while((candidate = reader.readLine()) != null) {
    		Long time = Long.parseLong(candidate.substring(0, candidate.indexOf(",")).trim());
    		if(time > lastResponseTime) {
    			lastResponseTime = time;
    			return candidate;
    		}
    	}
		return null;
    }
    
    /**
     * Checks that the response from the Bank is a valid response.
     * If not, then it is likely that the message to the Bank was either a replay attempt
     * or it had been tampered with.
     * 
     * @param response
     * @return
     */
    private boolean responseValid(String response) {
    	if(response.equals("FAIL")) {
    		System.out.println();
    		return false;
    	}
    	return true;
    }

    /* Clean up all open streams. */
    public void close() throws IOException {
        writer.close();
        reader.close();
    }

}
