package com.design.machinecoding.controller;

import com.design.machinecoding.dto.ApiResponse;
import com.design.machinecoding.dto.CreateProductRequest;
import com.design.machinecoding.dto.UpdateProductRequest;
import com.design.machinecoding.enums.Category;
import com.design.machinecoding.model.Product;
import com.design.machinecoding.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody CreateProductRequest req) {
        Product product = productService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(product, "Product created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Collection<Product>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(productService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<Product>>> getByCategory(@PathVariable Category category) {
        return ResponseEntity.ok(ApiResponse.success(productService.getByCategory(category)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateProductRequest req) {
        return ResponseEntity.ok(ApiResponse.success(productService.update(id, req), "Product updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted"));
    }
}
