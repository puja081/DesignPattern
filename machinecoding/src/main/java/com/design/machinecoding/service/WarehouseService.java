package com.design.machinecoding.service;

import com.design.machinecoding.dto.CreateWarehouseRequest;
import com.design.machinecoding.exception.ResourceNotFoundException;
import com.design.machinecoding.model.Warehouse;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WarehouseService {

    private final Map<Long, Warehouse> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public Warehouse create(CreateWarehouseRequest req) {
        Warehouse warehouse = Warehouse.builder()
                .id(idGen.getAndIncrement())
                .name(req.name())
                .location(req.location())
                .build();
        store.put(warehouse.getId(), warehouse);
        return warehouse;
    }

    public Warehouse getById(Long id) {
        Warehouse warehouse = store.get(id);
        if (warehouse == null) throw new ResourceNotFoundException("Warehouse", id);
        return warehouse;
    }

    public Collection<Warehouse> getAll() {
        return store.values();
    }
}
