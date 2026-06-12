package com.coconut.coconut_marketplace;

import com.coconut.coconut_marketplace.dto.CartItemDto;
import com.coconut.coconut_marketplace.entity.Order;
import com.coconut.coconut_marketplace.entity.OrderItem;
import com.coconut.coconut_marketplace.entity.Product;
import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.entity.User;
import com.coconut.coconut_marketplace.enums.Category;
import com.coconut.coconut_marketplace.enums.OrderStatus;
import com.coconut.coconut_marketplace.enums.ProductStatus;
import com.coconut.coconut_marketplace.enums.Role;
import com.coconut.coconut_marketplace.repository.OrderItemRepository;
import com.coconut.coconut_marketplace.repository.OrderRepository;
import com.coconut.coconut_marketplace.repository.ProductRepository;
import com.coconut.coconut_marketplace.repository.SellerProfileRepository;
import com.coconut.coconut_marketplace.repository.UserRepository;
import com.coconut.coconut_marketplace.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OrderServiceTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderService orderService;

    private User buyer;
    private SellerProfile seller;
    private Product product;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        sellerProfileRepository.deleteAll();
        userRepository.deleteAll();

        // Create Buyer
        buyer = userRepository.save(User.builder()
                .fullName("Buyer One")
                .email("buyer1@example.com")
                .password("password123")
                .role(Role.BUYER)
                .emailVerified(true)
                .build());

        // Create Seller
        User sellerUser = userRepository.save(User.builder()
                .fullName("Seller One")
                .email("seller1@example.com")
                .password("password123")
                .role(Role.SELLER)
                .emailVerified(true)
                .build());

        seller = sellerProfileRepository.save(SellerProfile.builder()
                .user(sellerUser)
                .storeName("Coconut Haven")
                .storeDescription("Pure coconuts only")
                .build());

        // Create Product
        product = productRepository.save(Product.builder()
                .name("Fresh Coconut")
                .description("Sweet and refreshing tender coconuts.")
                .price(new BigDecimal("50.00"))
                .stockQuantity(10)
                .category(Category.FRESH_COCONUT)
                .status(ProductStatus.ACTIVE)
                .imageUrl("/css/images/products/tender_coconut.png")
                .seller(seller)
                .build());
    }

    @Test
    void testCreateOrder_Success() {
        CartItemDto cartItem = CartItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .imageUrl(product.getImageUrl())
                .categoryDisplayName(product.getCategory().getDisplayName())
                .price(product.getPrice())
                .quantity(2)
                .storeName(seller.getStoreName())
                .stockQuantity(product.getStockQuantity())
                .build();

        Order shippingInfo = Order.builder()
                .fullName("Jane Doe")
                .email("jane@example.com")
                .phoneNumber("9876543210")
                .address("456 Farm Road")
                .city("Pollachi")
                .district("Coimbatore")
                .state("Tamil Nadu")
                .pincode("642001")
                .build();

        Order placedOrder = orderService.createOrderFromCart(buyer, Collections.singletonList(cartItem), shippingInfo);

        assertNotNull(placedOrder.getId());
        assertNotNull(placedOrder.getOrderNumber());
        assertTrue(placedOrder.getOrderNumber().startsWith("COC-"));
        assertEquals("COD", placedOrder.getPaymentMethod());
        assertEquals("PENDING", placedOrder.getPaymentStatus().name());
        assertEquals(new BigDecimal("100.00"), placedOrder.getSubtotal());
        assertEquals(new BigDecimal("50.00"), placedOrder.getDeliveryCharge()); // Subtotal < 500 -> 50 Delivery fee
        assertEquals(new BigDecimal("150.00"), placedOrder.getTotalAmount());

        // Verify stock is deducted
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(8, updatedProduct.getStockQuantity());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());

        // Verify items created
        assertEquals(1, placedOrder.getOrderItems().size());
        OrderItem placedItem = placedOrder.getOrderItems().get(0);
        assertEquals(product.getId(), placedItem.getProduct().getId());
        assertEquals(2, placedItem.getQuantity());
        assertEquals(OrderStatus.PENDING, placedItem.getStatus());
    }

    @Test
    void testCreateOrder_AutoOutOfStock() {
        CartItemDto cartItem = CartItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .imageUrl(product.getImageUrl())
                .categoryDisplayName(product.getCategory().getDisplayName())
                .price(product.getPrice())
                .quantity(10) // Purchase entire stock
                .storeName(seller.getStoreName())
                .stockQuantity(product.getStockQuantity())
                .build();

        Order shippingInfo = Order.builder()
                .fullName("Jane Doe")
                .email("jane@example.com")
                .phoneNumber("9876543210")
                .address("456 Farm Road")
                .city("Pollachi")
                .district("Coimbatore")
                .state("Tamil Nadu")
                .pincode("642001")
                .build();

        Order placedOrder = orderService.createOrderFromCart(buyer, Collections.singletonList(cartItem), shippingInfo);

        // Verify stock is 0 and status is OUT_OF_STOCK
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, updatedProduct.getStockQuantity());
        assertEquals(ProductStatus.OUT_OF_STOCK, updatedProduct.getStatus());
    }

    @Test
    void testCreateOrder_InsufficientStock_ThrowsException() {
        CartItemDto cartItem = CartItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .imageUrl(product.getImageUrl())
                .categoryDisplayName(product.getCategory().getDisplayName())
                .price(product.getPrice())
                .quantity(11) // Request more than 10 available
                .storeName(seller.getStoreName())
                .stockQuantity(product.getStockQuantity())
                .build();

        Order shippingInfo = Order.builder()
                .fullName("Jane Doe")
                .email("jane@example.com")
                .phoneNumber("9876543210")
                .address("456 Farm Road")
                .city("Pollachi")
                .district("Coimbatore")
                .state("Tamil Nadu")
                .pincode("642001")
                .build();

        assertThrows(IllegalArgumentException.class, () -> 
                orderService.createOrderFromCart(buyer, Collections.singletonList(cartItem), shippingInfo));
    }

    @Test
    void testUpdateItemStatus_Success() {
        CartItemDto cartItem = CartItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(2)
                .build();

        Order shippingInfo = Order.builder()
                .fullName("Jane Doe")
                .email("jane@example.com")
                .phoneNumber("9876543210")
                .address("456 Farm Road")
                .city("Pollachi")
                .district("Coimbatore")
                .state("Tamil Nadu")
                .pincode("642001")
                .build();

        Order placedOrder = orderService.createOrderFromCart(buyer, Collections.singletonList(cartItem), shippingInfo);
        OrderItem item = placedOrder.getOrderItems().get(0);

        // Confirm
        OrderItem confirmed = orderService.updateItemStatus(item.getId(), OrderStatus.CONFIRMED, seller);
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());

        // Ship
        OrderItem shipped = orderService.updateItemStatus(item.getId(), OrderStatus.SHIPPED, seller);
        assertEquals(OrderStatus.SHIPPED, shipped.getStatus());
    }

    @Test
    void testUpdateItemStatus_RestockOnCancel() {
        CartItemDto cartItem = CartItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(10) // Buy all stock
                .build();

        Order shippingInfo = Order.builder()
                .fullName("Jane Doe")
                .email("jane@example.com")
                .phoneNumber("9876543210")
                .address("456 Farm Road")
                .city("Pollachi")
                .district("Coimbatore")
                .state("Tamil Nadu")
                .pincode("642001")
                .build();

        Order placedOrder = orderService.createOrderFromCart(buyer, Collections.singletonList(cartItem), shippingInfo);
        OrderItem item = placedOrder.getOrderItems().get(0);

        // Verify product is OUT_OF_STOCK
        Product outOfStockProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, outOfStockProduct.getStockQuantity());
        assertEquals(ProductStatus.OUT_OF_STOCK, outOfStockProduct.getStatus());

        // Cancel
        orderService.updateItemStatus(item.getId(), OrderStatus.CANCELLED, seller);

        // Verify product stock is restored and toggles back to ACTIVE
        Product restoredProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(10, restoredProduct.getStockQuantity());
        assertEquals(ProductStatus.ACTIVE, restoredProduct.getStatus());
    }

    @Test
    void testUpdateItemStatus_SecurityException() {
        CartItemDto cartItem = CartItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(2)
                .build();

        Order shippingInfo = Order.builder()
                .fullName("Jane Doe")
                .email("jane@example.com")
                .phoneNumber("9876543210")
                .address("456 Farm Road")
                .city("Pollachi")
                .district("Coimbatore")
                .state("Tamil Nadu")
                .pincode("642001")
                .build();

        Order placedOrder = orderService.createOrderFromCart(buyer, Collections.singletonList(cartItem), shippingInfo);
        OrderItem item = placedOrder.getOrderItems().get(0);

        // Create hacker seller
        User hackerUser = userRepository.save(User.builder()
                .fullName("Hacker Seller")
                .email("hacker@example.com")
                .password("password123")
                .role(Role.SELLER)
                .emailVerified(true)
                .build());
        SellerProfile hackerSeller = sellerProfileRepository.save(SellerProfile.builder()
                .user(hackerUser)
                .storeName("Hacker Shop")
                .build());

        // Hacker attempts to update status
        assertThrows(SecurityException.class, () -> 
                orderService.updateItemStatus(item.getId(), OrderStatus.CONFIRMED, hackerSeller));
    }
}
