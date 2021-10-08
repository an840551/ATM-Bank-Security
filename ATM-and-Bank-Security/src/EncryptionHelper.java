import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class EncryptionHelper {

    private SecretKeySpec key;
    private Mac mac;
    private final int ACCEPTABLE_TIME_RANGE_MILLIS = 10000000;
    
    public EncryptionHelper(byte[] aesKeyRaw, byte[] macKeyRaw) {
	    // Generate the AES key, used for both encryption and decryption
		key = new SecretKeySpec(aesKeyRaw, "AES");
		
		// Generate the MAC hash
		SecretKeySpec macKey = new SecretKeySpec(macKeyRaw, "HmacSHA1");
	    try {
			mac = Mac.getInstance("HmacSHA1");
		    mac.init(macKey);
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }
    }

    /**
     * Encrypts the String str using AES with mode CBC and a random Initialization vector.
     * 
     * @param str
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     */
    public String encrypt(String str) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
    	// Initialize the necessary variables
    	SecureRandom r = new SecureRandom();
    	byte[] input = str.getBytes();
    	byte[] iv = new byte[16];
    	r.nextBytes(iv);
    	
    	// Prepare the cipher object
    	IvParameterSpec ips = new IvParameterSpec(iv);
		Cipher aesEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
		aesEncrypt.init(Cipher.ENCRYPT_MODE, key, ips);
		
		// Encrypt the input
		byte[] output = aesEncrypt.doFinal(input);
		
		// Add the IV to the encrypted message
		byte[] combined = new byte[output.length + 16];
		for(int i = 0; i < 16; i++) {
			combined[i] = iv[i];
		}
		for(int i = 16; i < combined.length; i++) {
			combined[i] = output[i - 16];
		}
		
		// Return the output as a hex string
    	return byteArrayToHexString(combined);
    }
    
    /**
     * Decrypts the String str by obtaining the initialization vector and using standard AES decryption with CBC.
     * 
     * @param str
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public String decrypt(String str) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
    	// Turn the hex string into a byte array
    	byte[] message = new byte[(str.length() >> 1) - 16], iv = new byte[16];
    	for(int i = 0, j = 0; i < str.length(); i += 2, j++) {
    		if(j < 16)
    			iv[j] = (byte) (Integer.parseInt(str.substring(i, i+2), 16));
    		else
    			message[j - 16] = (byte) (Integer.parseInt(str.substring(i, i+2), 16));
    	}
    	
    	// Set up the cipher object
    	IvParameterSpec ips = new IvParameterSpec(iv);
    	Cipher aesDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
		aesDecrypt.init(Cipher.DECRYPT_MODE, key, ips);
		
		// Decrypt the input
		byte[] output = aesDecrypt.doFinal(message);
    	
		// Return the human-readable string
    	return byteArrayToAsciiString(output);
    }
    
    /**
     * Secures a given message for communication between the ATM and the Bank.
     * It takes the current system time in milliseconds concatenated with the provided message and hashes that
     * to generate a MAC. The unhashed message is concatenated with the hashed message and returned as a bundle.
     * 
     * @param message
     * @return
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     */
    public String secureMessage(String message) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
    	String m = System.currentTimeMillis() + "," + encrypt(message);    	
    	String macked = macMessage(m);
    	//System.out.println(m + " secure=> " + macked);
    	return m + "," + macked;
    }
    
    /**
     * Runs the given message through the MAC function.
     * 
     * @param message
     * @return
     */
    public String macMessage(String message) {
    	//System.out.println(message + " => " + byteArrayToHexString(macked));
    	return byteArrayToHexString(mac.doFinal(message.getBytes()));
    }
    
    /**
     * Given a message generated using the secureMessage() function, returns the original, unhashed, unencrypted message.
     * However, there are a certain number of tests the message has to pass. Namely,
     * 	1. The unencrypted message (i.e., the string on the left side of the second comma) must hash to the 
     * 	   value on the right side of the second comma. This verifies that the message was not tampered with.
     *  2. The time value given in the unencrypted portion of the message must be within ACCEPTABLE_TIME_RANGE_MILLIS of
     *     the current system time.
     * Should both of these conditions be met, the decrypted message is returned.
     * 
     * @param macked
     * @return
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     */
    public String revealMessage(String macked) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
    	// Collecting the pieces we need to verify the message.
    	String[] parts = macked.split(",");
    	String timeStr = parts[0];
    	String encryptedMessage = parts[1];
    	String mackedMessage = parts[2];
    	
    	// Verify that the time and encrypted message hash to what it should
    	//String one = macMessage(timeStr + "," + encryptedMessage);
    	//System.out.println(timeStr + "," + encryptedMessage + " reveal=> " + one);
    	if(!macMessage(timeStr.trim() + "," + encryptedMessage).equals(mackedMessage)) {
    		System.err.println("Message has been tampered with.\n");
    		return null;
    	}
    	
    	// Verify time is within range    	    	
    	if(System.currentTimeMillis() - Long.parseLong(timeStr.trim()) > ACCEPTABLE_TIME_RANGE_MILLIS) {
    		System.err.println("Message no longer valid.\n");
    		return null;
    	}
    	
    	// Return decrypted message    	
    	return decrypt(encryptedMessage);
    }
    
	/**
	 * Takes an arbitrary byte array and turns it into a hex string.
	 * 
	 * @param arr
	 * @return
	 */
	protected static String byteArrayToHexString(byte[] arr) {
		String toReturn = "", temp;
		short elem;
		int tempLength;
		for(int i = 0; i < arr.length; i++) {
			elem = arr[i];
			if(elem < 0)
				elem += 256;
			temp = Integer.toHexString(elem).toUpperCase();
			tempLength = temp.length();
			if(tempLength == 0)
				temp = "00";
			else if(tempLength == 1)
				temp = "0" + temp;
			else if(tempLength >= 3)
				temp = temp.substring(tempLength - 2);
			toReturn += temp;
		}
		return toReturn;
	}
	
	/**
	 * Converts a byte array into an ASCII String.
	 * 
	 * @param arr
	 * @return
	 */
	protected static String byteArrayToAsciiString(byte[] arr) {
		String toReturn = "";
		for(int i = 0; i < arr.length; i++) {
			toReturn += (char) arr[i];
		}
		return toReturn;
	}
}
