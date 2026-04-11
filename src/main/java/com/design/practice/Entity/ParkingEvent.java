package com.design.practice.Entity;

/**
 * [OOP — Enum as a Type-Safe Constant Set]
 *
 * ParkingEvent defines the set of events that the Observer (ParkingBuilding)
 * can broadcast to its listeners.
 *
 * Key interview points:
 *  - Enums in Java are full classes — they can have fields, methods, and
 *    implement interfaces. They are type-safe alternatives to int/String constants.
 *  - Each enum constant is a singleton instance, guaranteed by the JVM.
 *  - Enums work well with switch statements (exhaustive checks) and are
 *    serialization-safe — no risk of creating duplicate instances via deserialization.
 *  - Using an enum for event types makes the Observer pattern cleaner:
 *    listeners can filter on specific events using == (identity comparison is
 *    safe and preferred for enums, no need for .equals()).
 */
public enum ParkingEvent {
    SPOT_OCCUPIED,
    SPOT_RELEASED,
    PARKING_FULL
}
