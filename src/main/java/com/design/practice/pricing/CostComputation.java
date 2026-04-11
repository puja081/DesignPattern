package com.design.practice.pricing;

import com.design.practice.Entity.Ticket;

/**
 * [Strategy Pattern — Context class for Pricing]
 *
 * CostComputation is the "Context" in the Strategy pattern. It holds
 * a reference to a PricingStrategy and delegates the actual calculation.
 *
 * Key interview points:
 *  - The Context doesn't implement the algorithm — it delegates.
 *    This is the essence of Strategy: the context owns the "what to do"
 *    (compute cost), while the strategy owns the "how to do it" (fixed vs hourly).
 *  - The strategy is injected via constructor (Constructor Injection), which
 *    is a form of Dependency Injection (DI). This makes testing easy:
 *    in unit tests you can pass a mock PricingStrategy.
 *  - 'final' on the strategy field means the pricing model can't be
 *    changed after construction. If you need runtime switching, remove
 *    final and add a setter — but final is safer and preferred when possible.
 */
public class CostComputation {
    private final PricingStrategy strategy;
     public CostComputation(PricingStrategy strategy){
         this.strategy = strategy;
     }
     public double compute(Ticket ticket){
         return strategy.calculate(ticket);
     }
}
