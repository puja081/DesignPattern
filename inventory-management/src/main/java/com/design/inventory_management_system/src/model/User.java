package com.design.inventory_management_system.src.model;

import java.util.Objects;

public class User {
    private final String id;
    private final String name;
    private final String email;
    private final UserRole role;

    public User(String id, String name, String email, UserRole role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }

    public boolean canManageInventory() {
        return role == UserRole.ADMIN;
    }

    public boolean canPlaceOrder() {
        return role == UserRole.CUSTOMER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return id.equals(((User) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("User{id='%s', name='%s', role=%s}", id, name, role);
    }
}
