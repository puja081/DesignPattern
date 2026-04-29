package com.design.machinecoding.service;

import com.design.machinecoding.dto.AddStockRequest;
import com.design.machinecoding.dto.InventoryResponse;
import com.design.machinecoding.exception.BadRequestException;
import com.design.machinecoding.model.Inventory;
import com.design.machinecoding.model.Product;
import com.design.machinecoding.model.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductService productService;
    private final WarehouseService warehouseService;

    private static final int LOW_STOCK_THRESHOLD = 5;

    // key = "warehouseId:productId"
    private final Map<String, Inventory> inventoryStore = new ConcurrentHashMap<>();

    private String key(Long warehouseId, Long productId) {
        return warehouseId + ":" + productId;
    }

    public InventoryResponse addStock(AddStockRequest req) {
        Product product = productService.getById(req.productId());
        Warehouse warehouse = warehouseService.getById(req.warehouseId());

        String k = key(req.warehouseId(), req.productId());
        inventoryStore.putIfAbsent(k, new Inventory(req.productId(), req.warehouseId(), 0));
        Inventory inv = inventoryStore.get(k);
        inv.restock(req.quantity());

        return toResponse(inv, product, warehouse);
    }

    public List<InventoryResponse> getByWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseService.getById(warehouseId);
        return inventoryStore.values().stream()
                .filter(inv -> inv.getWarehouseId().equals(warehouseId))
                .map(inv -> toResponse(inv, productService.getById(inv.getProductId()), warehouse))
                .toList();
    }

    public List<InventoryResponse> getByProduct(Long productId) {
        Product product = productService.getById(productId);
        return inventoryStore.values().stream()
                .filter(inv -> inv.getProductId().equals(productId))
                .map(inv -> toResponse(inv, product, warehouseService.getById(inv.getWarehouseId())))
                .toList();
    }

    public boolean checkAvailability(Long productId, int quantity) {
        int totalAvailable = inventoryStore.values().stream()
                .filter(inv -> inv.getProductId().equals(productId))
                .mapToInt(Inventory::getAvailableQuantity)
                .sum();
        return totalAvailable >= quantity;
    }

    /**
     * Reserves stock for a product from the first warehouse that has enough.
     * Returns the warehouseId or null if no warehouse can fulfill.
     */
    public Long reserveStock(Long productId, int quantity) {
        Inventory selected = inventoryStore.values().stream()
                .filter(inv -> inv.getProductId().equals(productId))
                .filter(inv -> inv.getAvailableQuantity() >= quantity)
                .findFirst()
                .orElse(null);

        if (selected == null) return null;

        boolean reserved = selected.reserve(quantity);
        if (!reserved) return null;

        if (selected.getAvailableQuantity() < LOW_STOCK_THRESHOLD) {
            Product p = productService.getById(productId);
            Warehouse w = warehouseService.getById(selected.getWarehouseId());
            System.out.printf("[LOW STOCK ALERT] %s in %s — only %d left%n",
                    p.getName(), w.getName(), selected.getAvailableQuantity());
        }

        return selected.getWarehouseId();
    }

    public void confirmStock(Long warehouseId, Long productId, int quantity) {
        String k = key(warehouseId, productId);
        Inventory inv = inventoryStore.get(k);
        if (inv == null) throw new BadRequestException("No inventory found for this warehouse-product pair");
        inv.confirmReservation(quantity);
    }

    public void releaseStock(Long warehouseId, Long productId, int quantity) {
        String k = key(warehouseId, productId);
        Inventory inv = inventoryStore.get(k);
        if (inv != null) inv.releaseReservation(quantity);
    }

    private InventoryResponse toResponse(Inventory inv, Product product, Warehouse warehouse) {
        return new InventoryResponse(
                inv.getProductId(),
                product.getName(),
                inv.getWarehouseId(),
                warehouse.getName(),
                inv.getTotalQuantity(),
                inv.getReservedQuantity(),
                inv.getAvailableQuantity()
        );
    }
}
