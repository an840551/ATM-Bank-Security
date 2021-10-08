import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * A BankProtocol processes local and remote commands sent to the Bank and writes to
 * or reads from the router as necessary. You can use whatever method you would like to
 * read from and write to the router, but this is an example to get you started.
 */

public class BankProtocol implements Protocol {

    private PrintWriter writer;
    private BufferedReader reader;
    private byte[] aesKeyRaw = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    private byte[] macKeyRaw = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    private EncryptionHelper encrypter;
    private Long lastCommandTime = new Long(0);

    public BankProtocol(InputStream inputStream, OutputStream outputStream) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        writer = new PrintWriter(outputStream, true);
        reader = new BufferedReader(new InputStreamReader(inputStream));
        encrypter = new EncryptionHelper(aesKeyRaw, macKeyRaw);
    }

    /* Process commands sent through the router. */
    public void processRemoteCommands() throws IOException {
        String input;

        while ((input = reader.readLine()) != null) {
        	try {
        		processRemoteCommand(input);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
    }

    /* Process commands from the command line. */
    public void processLocalCommands(BufferedReader stdIn, String prompt) throws IOException {
        String userInput;

        while((userInput = stdIn.readLine()) != null ) {
            processLocalCommand(userInput);
            System.out.print(prompt);
        }

        stdIn.close();
    }

    /* Process a remote command and write out the result. */
    private synchronized void processRemoteCommand(String command) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
    	
    	// Try to get the unobfuscated message sent by the ATM
    	String clear;
    	if((clear = encrypter.revealMessage(command)) == null) {
    		writer.println(encrypter.secureMessage("FAIL"));
    		return;
    	}
    	
    	// Make sure the time is valid before proceeding. That is, if this command was made at the same time or before
    	// the previous valid command, then this is a replay attack and must be prevented.
    	Long time = Long.parseLong(command.substring(0, command.indexOf(",")).trim());
    	if(time <= lastCommandTime) {
    		writer.println(encrypter.secureMessage("FAIL"));
    	} else {
    		lastCommandTime = time;
    	}
    	
    	// Split this clear message into chunks to start handling it
    	String[] parts = clear.split(":");
    	String action = parts[0], user = parts[1], pin, cardSecret, amount;
    	int intAmount;
    	
    	// Do the specified action
    	if(action.equals("AUTH")) {
    		pin = parts[2]; cardSecret = parts[3];
    		if(Bank.accounts.containsKey(user) && Bank.accounts.get(user).verify(pin, cardSecret))
    			writer.println(encrypter.secureMessage("AUTH:" + user + ":PASS"));
    		else
    			writer.println(encrypter.secureMessage("AUTH:" + user + ":FAIL"));
    		
    	} else if (action.equals("BALANCE")) {
    		if(Bank.accounts.containsKey(user))
    			writer.println(encrypter.secureMessage("BALANCE:" + user + ":" + Integer.toString(Bank.accounts.get(user).getBalance())));
    		else
    			writer.println("FAIL");
    		
    	} else if (action.equals("WITHDRAW")) {
    		amount = parts[2];
    		if(Bank.accounts.containsKey(user)) {
    			BankAccount current = Bank.accounts.get(user);
    			try {
    				intAmount = Integer.parseInt(amount);
    				if(intAmount <= 0)
    					writer.println(encrypter.secureMessage("WITHDRAW:" + user + ":FAIL"));
    				else if(current.withdraw(intAmount))
	    				writer.println(encrypter.secureMessage("WITHDRAW:" + user + ":SUCCEED"));
	    			else
	    				writer.println(encrypter.secureMessage("WITHDRAW:" + user + ":INSUFFICIENT"));
    			} catch (NumberFormatException e) {
    				writer.println(encrypter.secureMessage("WITHDRAW:" + user + ":FAIL"));
    			}
    		} else
				writer.println("FAIL");
    		
    	}
    }

    /* Process user input. */
    private synchronized void processLocalCommand(String command) {
    	// Split the command into parts that we can easily use in executing the command
    	String[] parts = command.split(" ");
    	
    	// No valid command will have fewer than two elements in parts[]
    	if(parts.length < 2) {
    		System.out.println();
    		return;
    	}
    	
    	// Values necessary for either action
    	String action = parts[0];
    	String user = parts[1];
    	
    	// Don't do anything if the user isn't one of the three we support
    	if(!Bank.accounts.containsKey(user)) {
    		System.out.println();
    		return;
    	}

    	// Perform the two valid actions and print an error for anything else
    	BankAccount currentAccount = Bank.accounts.get(user);
    	if(action.equals("balance")) {
		if(parts.length != 2) {
			System.out.println();
			return;
		}
    		System.out.println("$" + currentAccount.getBalance());
    		
    	} else if(action.equals("deposit")) {
    		// Output an error if an amount is not provided
    		if(parts.length != 3) {
    			System.out.println();
    			return;
    		}
    		
    		// Output an error if the amount is not a number
    		int amount;
    		try {
    			amount = Integer.parseInt(parts[2]);
    			if(amount <= 0) throw new NumberFormatException();
    		} catch (NumberFormatException e) {
    			System.out.println();
    			return;
    		}
    		
    		// We are good to deposit
    		currentAccount.deposit(amount);
    		System.out.println("$" + amount + " added to " + user + "'s account");
    	} else {
    		System.out.println();
    		return;
    	}
    	
    	// Extra space for clarity in the command line
    	System.out.println();
    }

    /* Clean up all open streams. */
    public void close() throws IOException {
        reader.close();
        writer.close();
    }
}
