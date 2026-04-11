package com.design.practice.pricing;

import com.design.practice.Entity.Ticket;

public class FixedPricingStartegy implements PricingStrategy{
    @Override
    public double calculate(Ticket ticket) {
        return 100;
    }
}
