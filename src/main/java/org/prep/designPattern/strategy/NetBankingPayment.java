package org.prep.designPattern.strategy;

public class NetBankingPayment implements PaymentStrategy {

    private final String bankName;

    public NetBankingPayment(String bankName) {
        this.bankName = bankName;
    }

    @Override
    public void pay(double amount) {
        System.out.printf("Transferred %.2f via net banking (%s)%n", amount, bankName);
    }
}
