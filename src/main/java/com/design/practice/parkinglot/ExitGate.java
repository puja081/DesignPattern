package com.design.practice.parkinglot;

import com.design.practice.Entity.Ticket;
import com.design.practice.payment.Payment;
import com.design.practice.pricing.CostComputation;

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
