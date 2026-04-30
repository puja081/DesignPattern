package com.design.machinecoding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddStockRequest(
        @NotNull Long warehouseId,
        @NotNull Long productId,
        @Positive int quantity
) {}
