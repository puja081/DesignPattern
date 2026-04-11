package com.design.practice.parkinglot;

import com.design.practice.Entity.ParkingEvent;
import com.design.practice.Entity.ParkingSpot;
import com.design.practice.Entity.Ticket;
import com.design.practice.Entity.Vehicle;
import com.design.practice.Events.ParkingEventListener;

import java.util.ArrayList;
import java.util.List;

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
                    return ticket;
                }
            }
        }
        throw new RuntimeException("Parking Full");
    }

    public void release(Ticket ticket) {
        ticket.getParkingLevel().unPark(ticket.getVehicle().getVehicleType(), ticket.getParkingSpot());
    }

}
