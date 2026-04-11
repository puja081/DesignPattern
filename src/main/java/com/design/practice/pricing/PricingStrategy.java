package com.design.practice.pricing;

import com.design.practice.Entity.Ticket;

public interface PricingStrategy {
    public double calculate(Ticket ticket);
}
