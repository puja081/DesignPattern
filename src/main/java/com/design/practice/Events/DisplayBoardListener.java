package com.design.practice.Events;

import com.design.practice.Entity.ParkingEvent;

public class DisplayBoardListener implements ParkingEventListener{
    @Override
    public void onEvent(ParkingEvent event, String message){
        if (event == ParkingEvent.PARKING_FULL) {
            System.out.println("[DISPLAY BOARD] PARKING FULL -- " + message);
        } else if (event == ParkingEvent.SPOT_RELEASED) {
            System.out.println("[DISPLAY BOARD] Spots available -- " + message);
        }

    }
}
