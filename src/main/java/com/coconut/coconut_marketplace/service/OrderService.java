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

        // Enforce sequential transitions
        if (currentStatus == OrderStatus.DELIVERED || currentStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot change status of a closed order item (" + currentStatus + ").");
        }

        if (currentStatus == OrderStatus.PENDING) {
            if (newStatus != OrderStatus.CONFIRMED && newStatus != OrderStatus.CANCELLED) {
                throw new IllegalStateException("Pending items can only transition to Confirmed or Cancelled.");
            }
        } else if (currentStatus == OrderStatus.CONFIRMED) {
            if (newStatus != OrderStatus.SHIPPED && newStatus != OrderStatus.CANCELLED) {
                throw new IllegalStateException("Confirmed items can only transition to Shipped or Cancelled.");
            }
        } else if (currentStatus == OrderStatus.SHIPPED) {
            if (newStatus != OrderStatus.DELIVERED) {
                throw new IllegalStateException("Shipped items can only transition to Delivered.");
            }
        }

        // Set timestamps
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (newStatus == OrderStatus.CONFIRMED) {
            orderItem.setConfirmedAt(now);
        } else if (newStatus == OrderStatus.SHIPPED) {
            orderItem.setShippedAt(now);
        } else if (newStatus == OrderStatus.DELIVERED) {
            orderItem.setDeliveredAt(now);
        } else if (newStatus == OrderStatus.CANCELLED) {
            orderItem.setCancelledAt(now);
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

    @Transactional
    public OrderItem cancelItemByBuyer(Long orderItemId, User buyer) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found with ID: " + orderItemId));

        if (!orderItem.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new SecurityException("You do not have permission to modify this order item.");
        }

        OrderStatus currentStatus = orderItem.getStatus();
        if (currentStatus != OrderStatus.PENDING && currentStatus != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel item once it is shipped or completed.");
        }

        // Restock inventory
        Product product = orderItem.getProduct();
        product.setStockQuantity(product.getStockQuantity() + orderItem.getQuantity());
        if (product.getStatus() == ProductStatus.OUT_OF_STOCK) {
            product.setStatus(ProductStatus.ACTIVE);
        }
        productRepository.save(product);

        orderItem.setStatus(OrderStatus.CANCELLED);
        orderItem.setCancelledAt(java.time.LocalDateTime.now());
        return orderItemRepository.save(orderItem);
    }

    @Transactional
    public OrderItem confirmDeliveryByBuyer(Long orderItemId, User buyer) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found with ID: " + orderItemId));

        if (!orderItem.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new SecurityException("You do not have permission to modify this order item.");
        }

        OrderStatus currentStatus = orderItem.getStatus();
        if (currentStatus != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Can only confirm delivery for shipped items.");
        }

        orderItem.setStatus(OrderStatus.DELIVERED);
        orderItem.setDeliveredAt(java.time.LocalDateTime.now());
        return orderItemRepository.save(orderItem);
    }

    public java.util.Map<String, Object> getBuyerStats(User buyer) {
        List<Order> orders = orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);
        long totalOrders = orders.size();
        long activeOrders = 0;
        long deliveredOrders = 0;

        for (Order o : orders) {
            boolean hasActive = false;
            boolean hasDelivered = false;
            for (OrderItem oi : o.getOrderItems()) {
                if (oi.getStatus() == OrderStatus.PENDING || oi.getStatus() == OrderStatus.CONFIRMED || oi.getStatus() == OrderStatus.SHIPPED) {
                    hasActive = true;
                }
                if (oi.getStatus() == OrderStatus.DELIVERED) {
                    hasDelivered = true;
                }
            }
            if (hasActive) {
                activeOrders++;
            } else if (hasDelivered) {
                deliveredOrders++;
            }
        }

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalOrders", totalOrders);
        stats.put("activeOrders", activeOrders);
        stats.put("deliveredOrders", deliveredOrders);
        return stats;
    }

    public List<Order> getOrdersByBuyer(User buyer) {
        return orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);
    }

    public List<OrderItem> getOrderItemsBySeller(SellerProfile seller) {
        return orderItemRepository.findBySellerOrderByCreatedAtDesc(seller);
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + id));
    }

    public OrderItem getOrderItemById(Long id) {
        return orderItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found with ID: " + id));
    }
}
