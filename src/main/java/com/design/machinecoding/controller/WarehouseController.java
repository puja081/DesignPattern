package com.design.machinecoding.controller;

import com.design.machinecoding.dto.ApiResponse;
import com.design.machinecoding.dto.CreateWarehouseRequest;
import com.design.machinecoding.model.Warehouse;
import com.design.machinecoding.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    public ResponseEntity<ApiResponse<Warehouse>> create(@Valid @RequestBody CreateWarehouseRequest req) {
        Warehouse warehouse = warehouseService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(warehouse, "Warehouse created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Collection<Warehouse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Warehouse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getById(id)));
    }
}
