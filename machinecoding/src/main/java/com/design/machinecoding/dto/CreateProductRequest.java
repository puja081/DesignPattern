package com.design.machinecoding.dto;

import com.design.machinecoding.enums.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProductRequest(
        @NotBlank String name,
        @NotNull Category category,
        @Positive double price,
        String description
) {}
