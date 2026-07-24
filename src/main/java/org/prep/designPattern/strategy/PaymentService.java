package org.prep.designPattern.strategy;

public class PaymentService {

    private PaymentStrategy paymentStrategy;

    public PaymentService(PaymentStrategy paymentStrategy) {
        this.paymentStrategy = paymentStrategy;
    }

    // strategy can be swapped at runtime -- the whole point of composing behavior
    // over a field instead of hardcoding it, see docs/day7/08-lld-design-patterns.md
    public void setPaymentStrategy(PaymentStrategy paymentStrategy) {
        this.paymentStrategy = paymentStrategy;
    }

    public void checkout(double amount) {
        paymentStrategy.pay(amount);
    }

    public static void main(String[] args) {
        PaymentService checkout = new PaymentService(new CreditCardPayment("4111111111111234"));
        checkout.checkout(250.00);

        checkout.setPaymentStrategy(new UpiPayment("ishan@upi"));
        checkout.checkout(499.99);

        checkout.setPaymentStrategy(new NetBankingPayment("HDFC Bank"));
        checkout.checkout(1200.00);
    }
}
