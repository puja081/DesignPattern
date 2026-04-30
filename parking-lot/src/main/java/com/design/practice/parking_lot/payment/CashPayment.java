package com.design.practice.parking_lot.payment;

/**
 * [Strategy Pattern — Concrete Strategy: Cash Payment]
 *
 * Key interview points:
 *  - @Override annotation tells the compiler: "I intend to override a
 *    supertype method." If the method signature doesn't match, compilation
 *    fails — catching typos early. Always use @Override.
 *  - Returns boolean so the caller (ExitGate) can decide what to do on
 *    failure. This is better than throwing from inside — it gives the caller
 *    control (e.g., retry, fallback to another payment method).
 */
public class CashPayment implements Payment {

    @Override
    public boolean pay(double amount) {
        System.out.println("Cash paid");
        return true;
    }
}
