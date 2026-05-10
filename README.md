# DesignPattern

A collection of design pattern implementations and machine coding practice projects in Java.

## Projects

### 1. Machine Coding — Order & Inventory Management System
**Path:** `src/main/java/com/design/machinecoding/`

A fully working Spring Boot REST API project for practicing 1-hour machine coding rounds. Features:
- Product, Warehouse, Inventory, and Order management APIs
- Saga pattern for order placement with rollback
- Reserve → Confirm → Release stock lifecycle
- Order state machine (PENDING → CONFIRMED → SHIPPED → DELIVERED / CANCELLED)
- Thread-safe inventory with `synchronized` + `ConcurrentHashMap`
- DataInitializer seeds sample data on startup
- Global exception handling with consistent API response format

**Run:**
```bash
./gradlew bootRun
# http://localhost:8080 — APIs ready with seeded data
```

See the [detailed README](src/main/java/com/design/machinecoding/README.md) for API reference, design patterns, interview tips, and curl examples.

### 2. Inventory Management System (Standalone)
**Path:** `src/main/java/com/design/inventory_management_system/`

Plain Java implementation of an inventory management system demonstrating core design patterns (Strategy, Observer, Saga) without Spring Boot. Single-file runnable demo.

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Build Tool | Gradle |
| Boilerplate | Lombok |
| Storage | In-memory (ConcurrentHashMap) |

## Quick Start

```bash
# Clone and run
git clone <repo-url>
cd DesignPattern
./gradlew bootRun

# Health check
curl http://localhost:8080/health

# Browse seeded products
curl http://localhost:8080/api/products
```
