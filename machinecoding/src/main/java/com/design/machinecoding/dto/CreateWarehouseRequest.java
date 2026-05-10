package com.design.machinecoding.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWarehouseRequest(
        @NotBlank String name,
        @NotBlank String location
) {}
