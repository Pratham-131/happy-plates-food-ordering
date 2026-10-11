package com.foodify.foodiesapi.service;

import com.foodify.foodiesapi.entity.OrderEntity;
import com.foodify.foodiesapi.enums.OrderStatus;
import com.foodify.foodiesapi.io.OrderRequest;
import com.foodify.foodiesapi.io.OrderResponse;
import com.foodify.foodiesapi.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final UserService userService;

    @Override
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getOrderedItems() == null || request.getOrderedItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order must contain at least one item");
        }
        OrderEntity order = OrderEntity.builder()
                .userId(userService.findByUserId())
                .userAddress(request.getUserAddress())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .orderedItems(request.getOrderedItems())
                .amount(request.getAmount())
                .paymentStatus("PENDING")
                .orderStatus(OrderStatus.PLACED.name())
                .build();
        return convertToResponse(orderRepository.save(order));
    }

    @Override
    public List<OrderResponse> getUserOrders() {
        return orderRepository.findByUserId(userService.findByUserId()).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getOrdersOfAllUsers() {
        return orderRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteOrder(String orderId) {
        OrderEntity order = orderRepository.findByIdAndUserId(orderId, userService.findByUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        orderRepository.delete(order);
    }

    @Override
    public void updateOrderStatus(String orderId, String status) {
        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        try {
            entity.setOrderStatus(OrderStatus.valueOf(status.toUpperCase(Locale.ROOT)).name());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported order status");
        }
        orderRepository.save(entity);
    }

    private OrderResponse convertToResponse(OrderEntity order) {
        return OrderResponse.builder()
                .id(order.getId())
                .amount(order.getAmount())
                .userAddress(order.getUserAddress())
                .userId(order.getUserId())
                .razorpayOrderId(order.getRazorpayOrderId())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .email(order.getEmail())
                .phoneNumber(order.getPhoneNumber())
                .orderedItems(order.getOrderedItems())
                .build();
    }
}


