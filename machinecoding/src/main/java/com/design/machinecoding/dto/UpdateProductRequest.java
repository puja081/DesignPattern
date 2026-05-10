package com.design.machinecoding.dto;

import jakarta.validation.constraints.Positive;

public record UpdateProductRequest(
        String name,
        @Positive Double price,
        String description
) {}
