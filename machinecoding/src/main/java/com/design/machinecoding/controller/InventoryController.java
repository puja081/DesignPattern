package com.design.machinecoding.controller;

import com.design.machinecoding.dto.AddStockRequest;
import com.design.machinecoding.dto.ApiResponse;
import com.design.machinecoding.dto.InventoryResponse;
import com.design.machinecoding.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/add-stock")
    public ResponseEntity<ApiResponse<InventoryResponse>> addStock(@Valid @RequestBody AddStockRequest req) {
        InventoryResponse response = inventoryService.addStock(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Stock added"));
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getByWarehouse(warehouseId)));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getByProduct(productId)));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAvailability(
            @RequestParam Long productId,
            @RequestParam int quantity) {
        boolean available = inventoryService.checkAvailability(productId, quantity);
        Map<String, Object> result = Map.of(
                "productId", productId,
                "requestedQuantity", quantity,
                "available", available
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
