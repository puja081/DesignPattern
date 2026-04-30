package com.design.practice.parking_lot.pricing;

import com.design.practice.parking_lot.Entity.Ticket;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * [Strategy Pattern — Concrete Strategy: Hourly Pricing]
 *
 * Calculates cost based on the number of hours the vehicle was parked.
 * Demonstrates how a different algorithm plugs into the same interface.
 *
 * Key interview points:
 *  - Uses java.time.Duration to compute elapsed time — the modern Java
 *    date/time API (Java 8+). Avoid java.util.Date in new code.
 *  - Math.ceil ensures partial hours are charged as full hours
 *    (e.g., 2.1 hours → charged for 3 hours). This is a business rule
 *    encapsulated inside the strategy.
 *  - To switch from Fixed to Hourly pricing, only the wiring in
 *    ParkingLotClient changes: new CostComputation(new HourlyPricingStrategy())
 *    — no other class is modified.
 */
public class HourlyPricingStartegy implements PricingStrategy {

    private static final double RATE_PER_HOUR = 50.0;

    @Override
    public double calculate(Ticket ticket) {
        Duration duration = Duration.between(ticket.getEntryTime(), LocalDateTime.now());
        long hours = (long) Math.ceil(duration.toMinutes() / 60.0);
        return Math.max(1, hours) * RATE_PER_HOUR;
    }
}
