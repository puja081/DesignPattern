package com.design.practice.parking_lot.parkinglot;

import com.design.practice.parking_lot.Entity.ParkingEvent;
import com.design.practice.parking_lot.Entity.ParkingSpot;
import com.design.practice.parking_lot.Entity.Ticket;
import com.design.practice.parking_lot.Entity.Vehicle;
import com.design.practice.parking_lot.Events.ParkingEventListener;
import com.design.practice.parking_lot.enums.VehicleType;

import java.util.ArrayList;
import java.util.List;

/**
 * [Observer Pattern — Subject / Event Publisher]
 * [OOP — Composition + Defensive Copying]
 *
 * ParkingBuilding is the central "Subject" in the Observer pattern.
 * It maintains a list of ParkingEventListeners and notifies them when
 * parking events occur (spot occupied, released, full).
 *
 * It also composes multiple ParkingLevels and delegates parking allocation
 * to them — demonstrating Composition ("has-a" relationship).
 *
 * Key interview points:
 *
 *  1. Defensive copy in the constructor:
 *     new ArrayList<>(parkingEventListeners) copies the incoming list into
 *     a mutable ArrayList. This is critical because:
 *       - List.of() returns an immutable list — calling add() on it throws
 *         UnsupportedOperationException.
 *       - Even with a mutable input, defensive copying prevents the caller
 *         from holding a reference to our internal list and mutating it
 *         behind our back (encapsulation violation).
 *     Rule of thumb: always defensively copy mutable collections in constructors.
 *
 *  2. Observer mechanics:
 *     addListener/removeListener manage subscriptions dynamically.
 *     notifyListeners iterates all registered observers — the Subject doesn't
 *     know or care what each observer does (loose coupling).
 *
 *  3. Allocation logic:
 *     allocate() iterates levels top-to-bottom, finding the first level with
 *     availability. This is a simple sequential strategy; in a real system
 *     this could be another Strategy (LevelSelectionStrategy).
 */
public class ParkingBuilding {
    private final List<ParkingLevel> parkingLevels;
    private final List<ParkingEventListener> parkingEventListeners;
    public ParkingBuilding(List<ParkingLevel> parkingLevels, List<ParkingEventListener> parkingEventListeners) {
        this.parkingLevels = parkingLevels;
        this.parkingEventListeners = new ArrayList<>(parkingEventListeners);
    }

    public void addListener(ParkingEventListener listener) {
        parkingEventListeners.add(listener);
    }
    public void removeListener(ParkingEventListener listener) {
        parkingEventListeners.remove(listener);
    }
    private void notifyListeners(ParkingEvent event, String message) {
        for (ParkingEventListener listener : parkingEventListeners) {
            listener.onEvent(event, message);
        }
    }

    public Ticket allocate(Vehicle vehicle) {
        for(ParkingLevel level : parkingLevels) {
            if(level.hasAvailability(vehicle.getVehicleType())){
                ParkingSpot spot = level.park(vehicle.getVehicleType());
                if(spot != null){
                    Ticket ticket = new Ticket(vehicle, level, spot);
                    System.out.println("Parking allocated at level: "
                            + level.getParkingLevelNumber()
                            + " spot: " + spot.getSpotId());
                    // Check if parking is now full
                    if (isParkingFull()) {
                        notifyListeners(ParkingEvent.PARKING_FULL,
                                "All spots occupied");
                    }
                    return ticket;
                }
            }
        }
        throw new RuntimeException("Parking Full");
    }
    private boolean isParkingFull() {
        for (ParkingLevel level : parkingLevels) {
            for (var type : VehicleType.values()) {
                if (level.hasAvailability(type)) return false;
            }
        }
        return true;
    }

    public void release(Ticket ticket) {
        ticket.getParkingLevel().unPark(ticket.getVehicle().getVehicleType(), ticket.getParkingSpot());
        notifyListeners(ParkingEvent.SPOT_RELEASED,
                "Level " + ticket.getParkingLevel().getParkingLevelNumber()
                        + " Spot " + ticket.getParkingSpot().getSpotId());
    }

}
