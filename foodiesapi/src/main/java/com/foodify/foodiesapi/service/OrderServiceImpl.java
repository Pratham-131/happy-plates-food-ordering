package com.foodify.foodiesapi.service;

import com.foodify.foodiesapi.entity.FoodEntity;
import com.foodify.foodiesapi.entity.OrderEntity;
import com.foodify.foodiesapi.enums.OrderStatus;
import com.foodify.foodiesapi.io.OrderItem;
import com.foodify.foodiesapi.io.OrderRequest;
import com.foodify.foodiesapi.io.OrderResponse;
import com.foodify.foodiesapi.repository.FoodRepository;
import com.foodify.foodiesapi.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final FoodRepository foodRepository;
    private final UserService userService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Override
    public OrderResponse createOrder(OrderRequest request) {
        if (request.getOrderedItems() == null || request.getOrderedItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order must contain at least one item");
        }

        BigDecimal amount = BigDecimal.ZERO;
        List<OrderItem> orderedItems = new ArrayList<>();
        for (OrderItem requestedItem : request.getOrderedItems()) {
            if (requestedItem == null || requestedItem.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item quantity must be greater than zero");
            }
            if (requestedItem.getFoodId() == null || requestedItem.getFoodId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown food item");
            }
            FoodEntity food = foodRepository.findById(requestedItem.getFoodId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown food item"));
            BigDecimal lineAmount = BigDecimal.valueOf(food.getPrice())
                    .multiply(BigDecimal.valueOf(requestedItem.getQuantity()));
            amount = amount.add(lineAmount);
            orderedItems.add(OrderItem.builder()
                    .foodId(food.getId())
                    .quantity(requestedItem.getQuantity())
                    .price(lineAmount.doubleValue())
                    .category(food.getCategory())
                    .imageUrl(food.getImageUrl())
                    .description(food.getDescription())
                    .name(food.getName())
                    .build());
        }
        BigDecimal subtotal = amount;
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = subtotal.signum() == 0 ? BigDecimal.ZERO : BigDecimal.TEN;
        amount = subtotal.add(tax).add(shipping).setScale(2, RoundingMode.HALF_UP);
        long amountInPaise = amount.movePointRight(2).longValueExact();

        String razorpayOrderId;
        try {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject options = new JSONObject();
            options.put("amount", amountInPaise);
            options.put("currency", "INR");
            razorpayOrderId = razorpayClient.orders.create(options).get("id");
        } catch (RazorpayException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create payment order", exception);
        }

        OrderEntity order = OrderEntity.builder()
                .userId(userService.findByUserId())
                .userAddress(request.getUserAddress())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .orderedItems(orderedItems)
                .amount(amount.doubleValue())
                .paymentStatus("PENDING")
                .orderStatus(OrderStatus.PLACED.name())
                .razorpayOrderId(razorpayOrderId)
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
        if (!"PENDING".equals(order.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be deleted");
        }
        orderRepository.delete(order);
    }

    @Override
    public void verifyPayment(String paymentId, String razorpayOrderId, String signature) {
        OrderEntity order = orderRepository.findByRazorpayOrderId(razorpayOrderId)
                .filter(existingOrder -> existingOrder.getUserId().equals(userService.findByUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment order"));
        if (!"PENDING".equals(order.getPaymentStatus()) || paymentId == null || signature == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment verification data");
        }

        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_payment_id", paymentId);
        attributes.put("razorpay_order_id", razorpayOrderId);
        attributes.put("razorpay_signature", signature);
        try {
            if (!Utils.verifyPaymentSignature(attributes, razorpayKeySecret)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment signature");
            }
        } catch (RazorpayException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment signature", exception);
        }

        order.setPaymentStatus("PAID");
        order.setRazorpayPaymentId(paymentId);
        order.setRazorpaySignature(signature);
        orderRepository.save(order);
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


