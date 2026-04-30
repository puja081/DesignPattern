package com.design.practice.parking_lot.Events;

import com.design.practice.parking_lot.Entity.ParkingEvent;

/**
 * =====================================================================
 * DESIGN PATTERN: Observer (Behavioral)
 * =====================================================================
 *
 * Intent: Define a one-to-many dependency so that when one object (Subject)
 *         changes state, all its dependents (Observers) are notified
 *         automatically.
 *
 * Structure in this project:
 *   - Observer interface → ParkingEventListener (this interface)
 *   - Concrete observers → DisplayBoardListener, AdminNotificationListener
 *   - Subject           → ParkingBuilding (maintains a list of listeners,
 *                          calls notifyListeners() on state changes)
 *
 * Why Observer here?
 *   When a spot is occupied/released, multiple independent systems need to
 *   react: the display board updates, admin gets notified, analytics fire, etc.
 *   Without Observer, ParkingBuilding would need to know about ALL these
 *   systems — tight coupling. With Observer, ParkingBuilding just iterates
 *   its listener list — it doesn't know or care who's listening.
 *
 * [OOP — Interface Segregation / Loose Coupling]
 *   ParkingBuilding depends on this interface, not on DisplayBoardListener
 *   or AdminNotificationListener directly. Adding a new listener (e.g.,
 *   AnalyticsListener) requires ZERO changes to ParkingBuilding.
 *
 * Real-world analogies:
 *   - Java Swing: ActionListener on a button
 *   - Spring: ApplicationEvent + @EventListener
 *   - Messaging: Pub/Sub (Kafka topics, SNS/SQS)
 */
public interface ParkingEventListener {
    public void onEvent(ParkingEvent parkingEvent, String message);
}
