package com.design.machinecoding.model;

import com.design.machinecoding.enums.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Product {
    private Long id;
    private String name;
    private Category category;
    private double price;
    private String description;
}
