package com.design.practice.parkinglot;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.Entity.Ticket;
import com.design.practice.Entity.Vehicle;
import com.design.practice.ParkingLotClient;
import com.design.practice.payment.Payment;

/**
 * =====================================================================
 * DESIGN PATTERN: Singleton (Creational)
 * =====================================================================
 *
 * Intent: Ensure a class has only ONE instance and provide a global
 *         point of access to it.
 *
 * Why Singleton here?
 *   A real-world parking lot is a single physical entity. Having multiple
 *   ParkingLot instances could cause double-booking of spots. Singleton
 *   guarantees exactly one ParkingLot manages all the state.
 *
 * Implementation: Double-Checked Locking (DCL) with volatile
 * ─────────────────────────────────────────────────────────
 *
 *   private static volatile ParkingLot instance;
 *
 *   1. 'volatile' keyword:
 *      Without volatile, Thread A might see a partially-constructed object
 *      (due to instruction reordering by the JVM/CPU). volatile guarantees
 *      that writes to 'instance' are visible to all threads AND that the
 *      constructor completes before the reference is published.
 *
 *   2. First null check (outside synchronized):
 *      After the instance is created, every subsequent call skips the
 *      synchronized block entirely → zero locking overhead in the common case.
 *
 *   3. synchronized (ParkingLot.class):
 *      Only one thread at a time enters this block. This prevents two threads
 *      from both seeing instance == null and both creating an instance.
 *
 *   4. Second null check (inside synchronized):
 *      Between the first check and acquiring the lock, another thread might
 *      have already created the instance. The second check prevents duplicates.
 *
 * Other Singleton approaches (for interviews):
 *   - Eager initialization:  static final INSTANCE = new ParkingLot(...)
 *     Simpler but creates the instance even if never used.
 *   - Enum singleton: enum ParkingLot { INSTANCE; }
 *     Simplest, serialization-safe, reflection-safe. Preferred by Effective Java.
 *   - Bill Pugh (static inner class): lazy, thread-safe, no synchronization.
 *
 * Note: The constructor is currently public — for a strict Singleton it should
 * be private to prevent external instantiation. Left public here for flexibility,
 * but in an interview, always mention making it private.
 *
 * [OOP — Composition]
 *   ParkingLot composes ParkingBuilding, EntranceGate, and ExitGate.
 *   It acts as a Facade — the client interacts with ParkingLot only,
 *   and ParkingLot delegates internally.
 */
public class ParkingLot {
    private static volatile ParkingLot instance;
    private final ParkingBuilding building;
    private final EntranceGate entranceGate;
    private final ExitGate exitGate;

    public ParkingLot(ParkingBuilding building, EntranceGate entranceGate, ExitGate exitGate) {
        this.building = building;
        this.entranceGate = entranceGate;
        this.exitGate = exitGate;
    }

    public static ParkingLot getInstance(ParkingBuilding building, EntranceGate entranceGate, ExitGate exitGate){
        if(instance == null) {
            synchronized (ParkingLot.class) {
                if(instance == null){
                    instance = new ParkingLot(building, entranceGate,exitGate);
                }
            }
        }
        return instance;
    }


    public Ticket vehicleArrives(Vehicle vehicle) {
        return entranceGate.enter(building, vehicle);
    }

    public void vehicleExits(Ticket ticket, Payment payment) {
        exitGate.completeExit(building, ticket, payment);
    }
}
