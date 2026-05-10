package com.design.practice.parking_lot.Events;

import com.design.practice.parking_lot.Entity.ParkingEvent;

/**
 * [Observer Pattern — Concrete Observer: Admin Notification]
 *
 * Sends an alert to the admin only when parking is full.
 * Demonstrates that different observers can react to different subsets
 * of the same event stream.
 *
 * Key interview points:
 *  - Adding this observer required zero changes to ParkingBuilding or
 *    DisplayBoardListener — the Open/Closed Principle in action.
 *  - In production, this would send an SMS/email via a notification
 *    service. You might make onEvent() async to avoid blocking the
 *    main flow while waiting for network I/O.
 */
public class AdminNotificationListener implements ParkingEventListener{
    @Override
    public void onEvent(ParkingEvent event, String message){
        if (event == ParkingEvent.PARKING_FULL) {
            System.out.println("[ADMIN SMS] Alert! Parking is full -- " + message);
        }
    }
}
