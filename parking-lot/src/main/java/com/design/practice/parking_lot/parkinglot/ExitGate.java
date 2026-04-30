package com.design.practice.parking_lot.parkinglot;

import com.design.practice.parking_lot.Entity.Ticket;
import com.design.practice.parking_lot.payment.Payment;
import com.design.practice.parking_lot.pricing.CostComputation;

/**
 * [OOP — Composition + Dependency Injection]
 *
 * ExitGate handles the exit flow: compute cost → collect payment → release spot.
 * It composes CostComputation (which itself uses Strategy for pricing).
 *
 * Key interview points:
 *
 *  1. Dependency Injection (DI) via constructor:
 *     CostComputation is injected, not created inside ExitGate. This means:
 *       - ExitGate doesn't decide the pricing strategy — the caller does.
 *       - In tests, you can inject a mock CostComputation.
 *       - In production, Spring/Guice would wire this automatically.
 *
 *  2. Method parameter injection:
 *     Payment is passed to completeExit() per-call (not stored as a field).
 *     This is intentional: each exit may use a different payment method.
 *     CostComputation is per-gate (field), Payment is per-transaction (param).
 *
 *  3. Fail-fast on payment failure:
 *     If payment.pay() returns false, we throw immediately — the gate stays
 *     closed. The spot is NOT released. This is transactional thinking:
 *     release only after successful payment.
 *
 *  4. Flow of the exit:
 *     completeExit() → calculateAmount() → CostComputation.compute()
 *                                          → PricingStrategy.calculate()
 *     This chain shows how Strategy + Composition create a flexible pipeline.
 */
public class ExitGate {
    private final CostComputation computation;

    public ExitGate(CostComputation computation) {
        this.computation = computation;
    }
    public void completeExit(ParkingBuilding building, Ticket ticket, Payment payment){
        double amount = calculateAmount(ticket);
        boolean paymentSuccess = payment.pay(amount);
        if(!paymentSuccess) {
            throw new RuntimeException("Payment failed. Exit denied.");
        }
        building.release(ticket);
        System.out.println("Exit successful. Gate opened.");
    }

    public double calculateAmount(Ticket ticket){
       return computation.compute(ticket);
    }
}
