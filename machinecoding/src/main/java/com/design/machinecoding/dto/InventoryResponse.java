package com.design.machinecoding.dto;

public record InventoryResponse(
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        int totalQuantity,
        int reservedQuantity,
        int availableQuantity
) {}
