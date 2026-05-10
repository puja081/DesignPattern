package com.design.practice.parking_lot.parkingspotmanager;

import com.design.practice.parking_lot.Entity.ParkingSpot;
import com.design.practice.parking_lot.LookUpStrategy.ParkingSpotLookupStrategy;
import com.design.practice.parking_lot.enums.VehicleType;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * [OOP — Inheritance + Composition + Thread Safety]
 *
 * ParkingSpotManager is the base class for vehicle-type-specific managers.
 * It holds the common logic (park, unPark, hasFreeSpot) so subclasses
 * (TwoWheelerSpotManager, FourWheelerSpotManager) don't duplicate it.
 *
 * Design patterns used here:
 *  - Strategy: The lookup algorithm is injected via the constructor
 *    (ParkingSpotLookupStrategy). The manager delegates spot selection
 *    to the strategy — it doesn't know the algorithm.
 *  - Factory product: Instances are created by ParkingSpotManagerFactory,
 *    not directly by client code.
 *
 * [Java Concurrency — ReentrantLock]
 *   Multiple entrance gates could try to park vehicles simultaneously.
 *   ReentrantLock protects the critical section (spot selection + occupy).
 *
 *   Key interview points on ReentrantLock vs synchronized:
 *    - ReentrantLock(true) creates a FAIR lock — threads acquire in FIFO order.
 *      synchronized is non-fair (no ordering guarantees).
 *    - ReentrantLock allows try-finally pattern for guaranteed unlock, even
 *      on exceptions. With synchronized, the JVM handles it, but you lose
 *      flexibility (e.g., tryLock with timeout).
 *    - ReentrantLock supports Condition objects for fine-grained wait/notify.
 *    - Rule of thumb: use synchronized for simple cases, ReentrantLock when
 *      you need fairness, tryLock, or multiple conditions.
 *
 * [OOP — Why lock-then-try-finally?]
 *   lock() is called OUTSIDE the try block. If lock() itself threw (it won't
 *   for ReentrantLock, but good practice), you wouldn't accidentally call
 *   unlock() on a lock you don't hold. The finally guarantees unlock even
 *   if the business logic throws — preventing deadlocks.
 */
public class ParkingSpotManager {
    private final ParkingSpotLookupStrategy strategy;
    private final ReentrantLock reentrantLock = new ReentrantLock(true);
    private final List<ParkingSpot> parkingSpotList;
    public ParkingSpotManager(ParkingSpotLookupStrategy strategy, List<ParkingSpot> parkingSpotList ) {
        this.strategy = strategy;
        this.parkingSpotList = parkingSpotList;
    }
    public ParkingSpot park(VehicleType type){
        reentrantLock.lock();
        try {
            ParkingSpot spot = strategy.selectSpot(parkingSpotList);
            if (spot == null) {
                return null;
            }
            spot.occupySpot();
            return spot;
        } finally {
            reentrantLock.unlock();
        }
    }
    public void unPark(ParkingSpot spot){
        reentrantLock.lock();
        try {
            spot.releaseSpot();
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * [Java 8 — Stream API + Method Reference]
     *
     * parkingSpotList.stream()         → creates a Stream<ParkingSpot>
     * .anyMatch(ParkingSpot::isSpotFree) → short-circuits on first free spot
     *
     * ParkingSpot::isSpotFree is a method reference — syntactic sugar for
     * (spot) -> spot.isSpotFree(). Prefer method references when they
     * improve readability.
     */
    public boolean hasFreeSpot() {
        reentrantLock.lock();
        try {
            return parkingSpotList.stream().anyMatch(ParkingSpot::isSpotFree);
        } finally {
            reentrantLock.unlock();
        }

    }
}
