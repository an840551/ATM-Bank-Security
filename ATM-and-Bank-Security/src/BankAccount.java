
public class BankAccount {
	
	private String name;
	private int balance;
	private String pin;
	private String cardValue;
	
	public BankAccount(String name, int balance, String pin, String cardValue) {
		this.name = name;
		this.balance = balance;
		this.pin = pin;
		this.cardValue = cardValue;
	}

	public int getBalance() {
		return balance;
	}
	
	public boolean deposit(int additionalFunds) {
		if(additionalFunds <= 0)
			return false;
		if(balance + additionalFunds < balance) {
			balance = Integer.MAX_VALUE;
		} else
			balance += additionalFunds;
		return true;
	}
	
	public boolean withdraw(int amount) {
		if(balance >= amount && amount > 0) {
			balance -= amount;
			return true;
		}
		return false;
	}

	public String getName() {
		return name;
	}
	
	public boolean verify(String pinCandidate, String cardValueCandidate) {
		return pinCandidate.equals(pin) && cardValueCandidate.equals(cardValue);
	}
	
}
