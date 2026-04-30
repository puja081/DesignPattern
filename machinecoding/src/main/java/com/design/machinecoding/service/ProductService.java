package com.design.machinecoding.service;

import com.design.machinecoding.dto.CreateProductRequest;
import com.design.machinecoding.dto.UpdateProductRequest;
import com.design.machinecoding.enums.Category;
import com.design.machinecoding.exception.ResourceNotFoundException;
import com.design.machinecoding.model.Product;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProductService {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public Product create(CreateProductRequest req) {
        Product product = Product.builder()
                .id(idGen.getAndIncrement())
                .name(req.name())
                .category(req.category())
                .price(req.price())
                .description(req.description())
                .build();
        store.put(product.getId(), product);
        return product;
    }

    public Product getById(Long id) {
        Product product = store.get(id);
        if (product == null) throw new ResourceNotFoundException("Product", id);
        return product;
    }

    public Collection<Product> getAll() {
        return store.values();
    }

    public List<Product> getByCategory(Category category) {
        return store.values().stream()
                .filter(p -> p.getCategory() == category)
                .toList();
    }

    public Product update(Long id, UpdateProductRequest req) {
        Product product = getById(id);
        if (req.name() != null) product.setName(req.name());
        if (req.price() != null) product.setPrice(req.price());
        if (req.description() != null) product.setDescription(req.description());
        return product;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) throw new ResourceNotFoundException("Product", id);
    }
}
