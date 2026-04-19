package com.design.inventory_management_system.model;

import java.util.Objects;

public class Product {
    private final String id;
    private final String name;
    private final Category category;
    private double price;
    private String description;

    public Product(String id, String name, Category category, double price, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Category getCategory() { return category; }
    public double getPrice() { return price; }
    public String getDescription() { return description; }

    public void setPrice(double price) { this.price = price; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        return id.equals(((Product) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Product{id='%s', name='%s', category=%s, price=%.2f}", id, name, category, price);
    }
}
