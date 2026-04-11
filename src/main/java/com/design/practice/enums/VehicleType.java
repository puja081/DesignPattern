package com.design.practice.enums;

/**
 * [OOP — Enum + Open/Closed Principle consideration]
 *
 * VehicleType classifies vehicles so the system can route them to the
 * correct ParkingSpotManager (via Factory) and the correct set of spots.
 *
 * Key interview points:
 *  - Adding a new type (e.g., HEAVY_VEHICLE) requires updating:
 *      1. This enum
 *      2. ParkingSpotManagerFactory switch-case
 *      3. A new ParkingSpotManager subclass (if behavior differs)
 *    This is a common trade-off — enums are great for a fixed set of types
 *    but violate Open/Closed Principle when extended. In interviews, mention
 *    that a Registry or Map<VehicleType, Supplier<ParkingSpotManager>> can
 *    reduce the number of places to change.
 *  - Enums are used as Map keys throughout (e.g., Map<VehicleType, ParkingSpotManager>
 *    in ParkingLevel). Enums have well-defined hashCode/equals, making them
 *    ideal, efficient Map keys.
 */
public enum VehicleType {
    TWO_WHEELER,
    FOUR_WHEELER
}
