package com.design.practice.pricing;

import com.design.practice.Entity.Ticket;

public class CostComputation {
    private final PricingStrategy strategy;
     public CostComputation(PricingStrategy strategy){
         this.strategy = strategy;
     }
     public double compute(Ticket ticket){
         return strategy.calculate(ticket);
     }
}
