package org.prep.designPattern.strategy;

public class CreditCardPayment implements PaymentStrategy {

    private final String cardNumber;

    public CreditCardPayment(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    @Override
    public void pay(double amount) {
        System.out.printf("Charged %.2f to credit card ending in %s%n",
                amount, cardNumber.substring(cardNumber.length() - 4));
    }
}
