package com.design.practice.pricing;

import com.design.practice.Entity.Ticket;

/**
 * =====================================================================
 * DESIGN PATTERN: Strategy (Behavioral) — Pricing Family
 * =====================================================================
 *
 * This is the SECOND Strategy in the project (the first was spot lookup).
 * Same pattern, different domain concern — pricing.
 *
 * Structure:
 *   - Strategy interface    → PricingStrategy (this interface)
 *   - Concrete strategies   → FixedPricingStrategy, HourlyPricingStrategy
 *   - Context (consumer)    → CostComputation (delegates to this interface)
 *
 * Why Strategy here?
 *   A parking lot may charge a flat rate, hourly rate, tiered rate, or
 *   surge pricing. Each algorithm is encapsulated in its own class.
 *   CostComputation doesn't know or care which one is active.
 *
 * [OOP — Single Responsibility Principle (SRP)]
 *   Each pricing class has ONE reason to change: its own pricing formula.
 *   CostComputation has ONE reason to change: orchestrating the computation.
 *   They change for different reasons → separate classes.
 */
public interface PricingStrategy {
    public double calculate(Ticket ticket);
}
