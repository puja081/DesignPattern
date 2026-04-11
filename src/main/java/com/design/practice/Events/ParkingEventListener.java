package com.design.practice.Events;

import com.design.practice.Entity.ParkingEvent;

public interface ParkingEventListener {
    public void onEvent(ParkingEvent parkingEvent, String message);
}
