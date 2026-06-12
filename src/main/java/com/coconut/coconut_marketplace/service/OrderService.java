package com.coconut.coconut_marketplace.service;

import com.coconut.coconut_marketplace.dto.CartItemDto;
import com.coconut.coconut_marketplace.entity.Order;
import com.coconut.coconut_marketplace.entity.OrderItem;
import com.coconut.coconut_marketplace.entity.Product;
import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.entity.User;
import com.coconut.coconut_marketplace.enums.OrderStatus;
import com.coconut.coconut_marketplace.enums.PaymentStatus;
import com.coconut.coconut_marketplace.enums.ProductStatus;
import com.coconut.coconut_marketplace.repository.OrderItemRepository;
import com.coconut.coconut_marketplace.repository.OrderRepository;
import com.coconut.coconut_marketplace.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    private final Random random = new Random();

    @Transactional
    public Order createOrderFromCart(User buyer, List<CartItemDto> cartItems, Order shippingInfo) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty. Cannot place an order.");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> itemsToCreate = new ArrayList<>();

        // Create a temporary holder to populate details
        Order order = Order.builder()
                .buyer(buyer)
                .fullName(shippingInfo.getFullName())
                .email(shippingInfo.getEmail())
                .phoneNumber(shippingInfo.getPhoneNumber())
                .address(shippingInfo.getAddress())
                .city(shippingInfo.getCity())
                .district(shippingInfo.getDistrict())
                .state(shippingInfo.getState())
                .pincode(shippingInfo.getPincode())
                .paymentMethod("COD")
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        // 1. Process items and deduct inventory
        for (CartItemDto item : cartItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProductName()));

            if (product.getStatus() == ProductStatus.DISABLED) {
                throw new IllegalStateException("Product " + product.getName() + " is currently unavailable.");
            }

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for product " + product.getName() 
                        + ". Available: " + product.getStockQuantity());
            }

            // Deduct stock
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            if (product.getStockQuantity() == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
            }
            productRepository.save(product);

            // Compute totals
            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(itemTotal);

            // Build item entity
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .price(product.getPrice())
                    .quantity(item.getQuantity())
                    .totalPrice(itemTotal)
                    .seller(product.getSeller())
                    .status(OrderStatus.PENDING)
                    .build();

            itemsToCreate.add(orderItem);
        }

        // 2. Set financial charges
        BigDecimal deliveryCharge = subtotal.compareTo(BigDecimal.valueOf(500)) >= 0 
                ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP) 
                : BigDecimal.valueOf(50).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(deliveryCharge).setScale(2, java.math.RoundingMode.HALF_UP);

        order.setSubtotal(subtotal);
        order.setDeliveryCharge(deliveryCharge);
        order.setTotalAmount(totalAmount);
        order.setOrderItems(itemsToCreate);

        // 3. Generate unique order number
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seqPart = random.nextInt(9000) + 1000; // 1000 to 9999
        order.setOrderNumber("COC-" + datePart + "-" + seqPart);

        return orderRepository.save(order);
    }

    @Transactional
    public OrderItem updateItemStatus(Long orderItemId, OrderStatus newStatus, SellerProfile seller) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found with ID: " + orderItemId));

        if (!orderItem.getSeller().getId().equals(seller.getId())) {
            throw new SecurityException("You do not have permission to modify this order item.");
        }

        OrderStatus currentStatus = orderItem.getStatus();
        if (currentStatus == newStatus) {
            return orderItem;
        }

        // If transitioning to CANCELLED, restock the product inventory
        if (newStatus == OrderStatus.CANCELLED) {
            Product product = orderItem.getProduct();
            product.setStockQuantity(product.getStockQuantity() + orderItem.getQuantity());
            
            // If the product was OUT_OF_STOCK, flip it back to ACTIVE since we restocked
            if (product.getStatus() == ProductStatus.OUT_OF_STOCK) {
                product.setStatus(ProductStatus.ACTIVE);
            }
            productRepository.save(product);
        }

        orderItem.setStatus(newStatus);
        return orderItemRepository.save(orderItem);
    }

    public List<Order> getOrdersByBuyer(User buyer) {
        return orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);
    }

    public List<OrderItem> getOrderItemsBySeller(SellerProfile seller) {
        return orderItemRepository.findBySellerOrderByCreatedAtDesc(seller);
    }
}
