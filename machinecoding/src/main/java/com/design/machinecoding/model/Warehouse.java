package com.design.machinecoding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Warehouse {
    private Long id;
    private String name;
    private String location;
}
