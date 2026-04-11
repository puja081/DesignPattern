package com.design.practice.Events;

import com.design.practice.Entity.ParkingEvent;

public class AdminNotificationListener implements ParkingEventListener{
    @Override
    public void onEvent(ParkingEvent event, String message){
        if (event == ParkingEvent.PARKING_FULL) {
            System.out.println("[ADMIN SMS] Alert! Parking is full -- " + message);
        }
    }
}
