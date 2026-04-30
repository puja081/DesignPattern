package com.design.machinecoding.config;

import com.design.machinecoding.dto.AddStockRequest;
import com.design.machinecoding.dto.CreateProductRequest;
import com.design.machinecoding.dto.CreateWarehouseRequest;
import com.design.machinecoding.enums.Category;
import com.design.machinecoding.service.InventoryService;
import com.design.machinecoding.service.ProductService;
import com.design.machinecoding.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;

    @Override
    public void run(String... args) {
        log.info("Initializing sample data...");

        var laptop = productService.create(new CreateProductRequest(
                "MacBook Pro", Category.ELECTRONICS, 2499.99, "16-inch M3 Max"));
        var phone = productService.create(new CreateProductRequest(
                "iPhone 15", Category.ELECTRONICS, 999.99, "Pro Max 256GB"));
        var shirt = productService.create(new CreateProductRequest(
                "Polo Shirt", Category.CLOTHING, 49.99, "Cotton, Blue, L"));
        var headphones = productService.create(new CreateProductRequest(
                "Sony WH-1000XM5", Category.ELECTRONICS, 349.99, "Noise Cancelling"));
        var shoes = productService.create(new CreateProductRequest(
                "Running Shoes", Category.SPORTS, 129.99, "Nike Air Max"));

        var bangalore = warehouseService.create(new CreateWarehouseRequest(
                "Bangalore Hub", "Whitefield, Bangalore"));
        var mumbai = warehouseService.create(new CreateWarehouseRequest(
                "Mumbai Hub", "Andheri, Mumbai"));
        var delhi = warehouseService.create(new CreateWarehouseRequest(
                "Delhi Hub", "Gurugram, Delhi NCR"));

        inventoryService.addStock(new AddStockRequest(bangalore.getId(), laptop.getId(), 50));
        inventoryService.addStock(new AddStockRequest(bangalore.getId(), phone.getId(), 100));
        inventoryService.addStock(new AddStockRequest(bangalore.getId(), shirt.getId(), 200));
        inventoryService.addStock(new AddStockRequest(bangalore.getId(), headphones.getId(), 75));
        inventoryService.addStock(new AddStockRequest(bangalore.getId(), shoes.getId(), 60));

        inventoryService.addStock(new AddStockRequest(mumbai.getId(), laptop.getId(), 30));
        inventoryService.addStock(new AddStockRequest(mumbai.getId(), phone.getId(), 80));
        inventoryService.addStock(new AddStockRequest(mumbai.getId(), headphones.getId(), 50));

        inventoryService.addStock(new AddStockRequest(delhi.getId(), shirt.getId(), 150));
        inventoryService.addStock(new AddStockRequest(delhi.getId(), shoes.getId(), 90));

        log.info("Sample data initialized!");
        log.info("Products: {} | Warehouses: {}", productService.getAll().size(), warehouseService.getAll().size());
        log.info("Test APIs at http://localhost:8080/api");
        log.info("Health check: http://localhost:8080/health");
    }
}
