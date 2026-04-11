package com.design.practice.LookUpStrategy;

import com.design.practice.Entity.ParkingSpot;

import java.util.List;

/**
 * =====================================================================
 * DESIGN PATTERN: Strategy (Behavioral)
 * =====================================================================
 *
 * Intent: Define a family of algorithms, encapsulate each one, and make
 *         them interchangeable. Strategy lets the algorithm vary
 *         independently from the clients that use it.
 *
 * Structure in this project:
 *   - Strategy interface    → ParkingSpotLookupStrategy (this interface)
 *   - Concrete strategy     → RandomLookupStrategy (first-available)
 *   - Context (consumer)    → ParkingSpotManager (holds a reference to this interface)
 *
 * Why Strategy here?
 *   Different parking lots may want different spot-selection policies:
 *   first-available, nearest-to-entrance, nearest-to-elevator, etc.
 *   By coding to this interface, ParkingSpotManager doesn't care which
 *   algorithm is plugged in — you swap strategies without touching the manager.
 *
 * [OOP — Programming to an Interface, not an Implementation]
 *   ParkingSpotManager stores a ParkingSpotLookupStrategy reference, not
 *   a RandomLookupStrategy. This decouples the manager from any concrete
 *   algorithm. In an interview, this is often called "Dependency Inversion
 *   Principle" (DIP) — high-level modules depend on abstractions.
 *
 * How to extend:
 *   Create a new class like NearestToEntranceLookupStrategy that implements
 *   this interface. Inject it at construction time — zero changes to existing code.
 */
public interface ParkingSpotLookupStrategy {

    ParkingSpot selectSpot(List<ParkingSpot> spots);
}
