package com.design.practice.pricing;

import com.design.practice.Entity.Ticket;

/**
 * [Strategy Pattern — Concrete Strategy: Fixed Pricing]
 *
 * Returns a fixed amount regardless of parking duration.
 * In a real system, the fixed amount could come from a config file or database.
 *
 * Key interview points:
 *  - This class demonstrates how simple a concrete strategy can be.
 *    The power of the pattern is not in the complexity of each strategy,
 *    but in the ability to swap them at runtime without if/else chains.
 *  - Without Strategy, you'd have:
 *      if (type == FIXED) return 100;
 *      else if (type == HOURLY) return hours * rate;
 *      else if (type == TIERED) ...
 *    Every new pricing model would modify this method — violating Open/Closed.
 */
public class FixedPricingStartegy implements PricingStrategy{
    @Override
    public double calculate(Ticket ticket) {
        return 100;
    }
}
