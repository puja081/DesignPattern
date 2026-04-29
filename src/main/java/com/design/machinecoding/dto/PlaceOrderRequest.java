package com.design.machinecoding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(
        @NotBlank String customerName,
        @NotEmpty @Valid List<OrderItemRequest> items
) {}
