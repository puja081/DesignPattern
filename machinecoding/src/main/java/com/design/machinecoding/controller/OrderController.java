package com.design.machinecoding.controller;

import com.design.machinecoding.dto.ApiResponse;
import com.design.machinecoding.dto.PlaceOrderRequest;
import com.design.machinecoding.model.Order;
import com.design.machinecoding.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<Order>> placeOrder(@Valid @RequestBody PlaceOrderRequest req) {
        Order order = orderService.placeOrder(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(order, "Order placed"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Collection<Order>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Order>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getById(id)));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Order>> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.confirmOrder(id), "Order confirmed"));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Order>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id), "Order cancelled"));
    }

    @PutMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<Order>> ship(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.shipOrder(id), "Order shipped"));
    }

    @PutMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<Order>> deliver(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.deliverOrder(id), "Order delivered"));
    }
}
