package com.design.practice.parking_lot.payment;

/**
 * =====================================================================
 * DESIGN PATTERN: Strategy (Behavioral) — Payment Family
 * =====================================================================
 *
 * This is the THIRD Strategy in the project. The pattern repeats because
 * it's applicable wherever you have multiple interchangeable algorithms.
 *
 * Structure:
 *   - Strategy interface    → Payment (this interface)
 *   - Concrete strategies   → CashPayment, UpiPayment
 *   - Context (consumer)    → ExitGate (calls payment.pay(amount))
 *
 * [OOP — Polymorphism]
 *   ExitGate calls payment.pay(amount) without knowing if it's cash or UPI.
 *   At runtime, the JVM dispatches the call to the correct implementation.
 *   This is runtime polymorphism (dynamic dispatch) — the most frequently
 *   asked OOP concept in interviews.
 *
 *   ExitGate code:  payment.pay(amount)  ← same line handles cash, UPI, card, etc.
 *   Without polymorphism you'd need: if (type == CASH) ... else if (type == UPI) ...
 *
 * How to extend:
 *   Add CardPayment implements Payment — no changes to ExitGate or any other class.
 */
public interface Payment {

    public boolean pay(double amount);
}
