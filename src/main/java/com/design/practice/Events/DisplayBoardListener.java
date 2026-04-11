package com.design.practice.Events;

import com.design.practice.Entity.ParkingEvent;

/**
 * [Observer Pattern — Concrete Observer: Display Board]
 *
 * Reacts to parking events by updating the display board.
 * Only cares about PARKING_FULL and SPOT_RELEASED events — it filters
 * and ignores irrelevant ones.
 *
 * Key interview points:
 *  - Each observer decides independently which events matter to it.
 *    The Subject (ParkingBuilding) broadcasts everything; observers filter.
 *    This keeps the Subject simple and the observers autonomous.
 *  - Enum comparison uses == instead of .equals() — this is correct and
 *    preferred for enums in Java. Enums are singletons, so identity
 *    comparison is both safe and slightly faster.
 *  - In a real system, this would call a display hardware API rather
 *    than System.out.println.
 */
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
