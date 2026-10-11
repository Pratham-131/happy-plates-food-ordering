package com.foodify.foodiesapi.service;

import com.foodify.foodiesapi.io.OrderRequest;
import com.foodify.foodiesapi.io.OrderResponse;

import java.util.List;
public interface OrderService {
    OrderResponse createOrder(OrderRequest request);

    List<OrderResponse> getUserOrders();

    List<OrderResponse> getOrdersOfAllUsers();

    void verifyPayment(String razorpayPaymentId, String razorpayOrderId, String razorpaySignature);

    void deleteOrder(String orderId);

    void updateOrderStatus(String orderId, String status);
}
