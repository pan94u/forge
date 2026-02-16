---
name: domain-inventory
description: >
  Inventory domain expertise. Stock management, reservations,
  warehouse operations, and availability checks.
trigger: when working with inventory, stock, warehouse, or reservation code
tags: [domain, inventory, stock, warehouse, reservation]
---

# Inventory Domain

## Domain Model
- **StockUnit**: Physical stock per SKU per warehouse (available, reserved, damaged)
- **Reservation**: Temporary hold on stock tied to an order (expires after TTL)
- **Warehouse**: Physical location with capacity and fulfillment priority

## Business Rules
1. **Available = Physical - Reserved - Damaged**
2. **Reservations expire** after 15 minutes if order not confirmed
3. **FIFO fulfillment**: Oldest stock dispatched first
4. **Multi-warehouse**: Check all warehouses, prefer nearest to customer
5. **Oversell protection**: Never allow `available < 0` (pessimistic locking)

## Code Entry Points
- `InventoryController` — `/api/v1/inventory`
- `StockService` — Stock level management
- `ReservationService` — Reserve/release stock
- `InventoryEventListener` — Handles OrderConfirmed → ReserveStock
