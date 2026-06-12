package com.coconut.coconut_marketplace;

import com.coconut.coconut_marketplace.entity.Product;
import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.entity.User;
import com.coconut.coconut_marketplace.enums.Category;
import com.coconut.coconut_marketplace.enums.ProductStatus;
import com.coconut.coconut_marketplace.enums.Role;
import com.coconut.coconut_marketplace.repository.ProductRepository;
import com.coconut.coconut_marketplace.repository.SellerProfileRepository;
import com.coconut.coconut_marketplace.repository.UserRepository;
import com.coconut.coconut_marketplace.repository.OrderRepository;
import com.coconut.coconut_marketplace.repository.OrderItemRepository;
import com.coconut.coconut_marketplace.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ProductServiceTests {

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
    private ProductService productService;

    private SellerProfile seller1;
    private SellerProfile seller2;

    @BeforeEach
    void setUp() {
        // Clean database tables
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        sellerProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Create Seller 1
        User u1 = User.builder()
                .fullName("Seller One")
                .email("seller1@example.com")
                .password("password123")
                .role(Role.SELLER)
                .emailVerified(true)
                .build();
        User savedU1 = userRepository.save(u1);
        seller1 = sellerProfileRepository.save(SellerProfile.builder()
                .user(savedU1)
                .storeName("Store One")
                .storeDescription("Selling fresh coconuts from farm")
                .build());

        // Create Seller 2
        User u2 = User.builder()
                .fullName("Seller Two")
                .email("seller2@example.com")
                .password("password123")
                .role(Role.SELLER)
                .emailVerified(true)
                .build();
        User savedU2 = userRepository.save(u2);
        seller2 = sellerProfileRepository.save(SellerProfile.builder()
                .user(savedU2)
                .storeName("Store Two")
                .storeDescription("Teal and fiber products")
                .build());
    }

    @Test
    void testCreateProduct_Success() {
        Product product = Product.builder()
                .name("Tender Coconut Fresh")
                .description("Sweet and refreshing farm fresh tender coconuts.")
                .price(new BigDecimal("60.00"))
                .stockQuantity(10)
                .category(Category.FRESH_COCONUT)
                .imageUrl("/css/images/products/tender_coconut.png")
                .build();

        Product saved = productService.createProduct(product, seller1);

        assertNotNull(saved.getId());
        assertEquals(seller1.getId(), saved.getSeller().getId());
        assertEquals(ProductStatus.ACTIVE, saved.getStatus()); // Stock > 0 -> Active
        assertEquals(Category.FRESH_COCONUT, saved.getCategory());
    }

    @Test
    void testCreateProduct_AutoOutOfStock() {
        Product product = Product.builder()
                .name("Tender Coconut Fresh")
                .description("Sweet and refreshing farm fresh tender coconuts.")
                .price(new BigDecimal("60.00"))
                .stockQuantity(0)
                .category(Category.FRESH_COCONUT)
                .imageUrl("/css/images/products/tender_coconut.png")
                .build();

        Product saved = productService.createProduct(product, seller1);

        assertEquals(ProductStatus.OUT_OF_STOCK, saved.getStatus()); // Stock = 0 -> Out of Stock
    }

    @Test
    void testUpdateProduct_AutoStatusChanges() {
        Product product = Product.builder()
                .name("Organic Coconut Oil")
                .description("Pure cold pressed organic virgin coconut oil.")
                .price(new BigDecimal("250.00"))
                .stockQuantity(15)
                .category(Category.COCONUT_OIL)
                .imageUrl("/css/images/products/coconut_oil.png")
                .build();

        Product saved = productService.createProduct(product, seller1);
        assertEquals(ProductStatus.ACTIVE, saved.getStatus());

        // Update stock to 0
        Product updateDetails = Product.builder()
                .name("Organic Coconut Oil")
                .description("Pure cold pressed organic virgin coconut oil.")
                .price(new BigDecimal("250.00"))
                .stockQuantity(0)
                .category(Category.COCONUT_OIL)
                .imageUrl("/css/images/products/coconut_oil.png")
                .status(ProductStatus.ACTIVE)
                .build();

        Product updated = productService.updateProduct(saved.getId(), updateDetails, seller1);
        assertEquals(ProductStatus.OUT_OF_STOCK, updated.getStatus()); // Auto toggled to Out of Stock

        // Restock to 5
        updateDetails.setStockQuantity(5);
        Product restocked = productService.updateProduct(saved.getId(), updateDetails, seller1);
        assertEquals(ProductStatus.ACTIVE, restocked.getStatus()); // Auto toggled to Active
    }

    @Test
    void testUpdateProduct_KeepManualDisabled() {
        Product product = Product.builder()
                .name("Organic Coconut Oil")
                .description("Pure cold pressed organic virgin coconut oil.")
                .price(new BigDecimal("250.00"))
                .stockQuantity(15)
                .category(Category.COCONUT_OIL)
                .imageUrl("/css/images/products/coconut_oil.png")
                .build();

        Product saved = productService.createProduct(product, seller1);
        
        // Manually disable even though stock > 0
        Product updateDetails = Product.builder()
                .name("Organic Coconut Oil")
                .description("Pure cold pressed organic virgin coconut oil.")
                .price(new BigDecimal("250.00"))
                .stockQuantity(15)
                .category(Category.COCONUT_OIL)
                .imageUrl("/css/images/products/coconut_oil.png")
                .status(ProductStatus.DISABLED)
                .build();

        Product updated = productService.updateProduct(saved.getId(), updateDetails, seller1);
        assertEquals(ProductStatus.DISABLED, updated.getStatus()); // Maintained manual disabled status
    }

    @Test
    void testValidateProduct_ThrowsException() {
        // Validation fails for name too short
        Product product = Product.builder()
                .name("C")
                .description("Sweet and refreshing farm fresh tender coconuts.")
                .price(new BigDecimal("60.00"))
                .stockQuantity(10)
                .category(Category.FRESH_COCONUT)
                .imageUrl("/css/images/products/tender_coconut.png")
                .build();

        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(product, seller1));

        // Validation fails for negative price
        product.setName("Good Coconut");
        product.setPrice(new BigDecimal("-1.00"));
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(product, seller1));
    }

    @Test
    void testOwnershipSecurityException() {
        Product product = Product.builder()
                .name("Coir Rope Bundle")
                .description("Durable coir fiber ropes for farming and packaging.")
                .price(new BigDecimal("120.00"))
                .stockQuantity(10)
                .category(Category.COIR_PRODUCTS)
                .imageUrl("/css/images/products/coir_rope.png")
                .build();

        Product saved = productService.createProduct(product, seller1);

        // Seller 2 attempts to update Seller 1's product
        Product updateDetails = Product.builder()
                .name("Hacked Rope Bundle")
                .description("Durable coir fiber ropes for farming and packaging.")
                .price(new BigDecimal("10.00"))
                .stockQuantity(100)
                .category(Category.COIR_PRODUCTS)
                .imageUrl("/css/images/products/coir_rope.png")
                .status(ProductStatus.ACTIVE)
                .build();

        assertThrows(SecurityException.class, () -> productService.updateProduct(saved.getId(), updateDetails, seller2));

        // Seller 2 attempts to delete Seller 1's product
        assertThrows(SecurityException.class, () -> productService.deleteProduct(saved.getId(), seller2));
    }

    @Test
    void testGetProductsBySeller() {
        Product p1 = Product.builder()
                .name("Product A")
                .description("Sweet and refreshing farm fresh tender coconuts.")
                .price(new BigDecimal("60.00"))
                .stockQuantity(10)
                .category(Category.FRESH_COCONUT)
                .imageUrl("/css/images/products/tender_coconut.png")
                .build();
        productService.createProduct(p1, seller1);

        Product p2 = Product.builder()
                .name("Product B")
                .description("Sweet and refreshing farm fresh tender coconuts.")
                .price(new BigDecimal("40.00"))
                .stockQuantity(20)
                .category(Category.FRESH_COCONUT)
                .imageUrl("/css/images/products/tender_coconut.png")
                .build();
        productService.createProduct(p2, seller1);

        List<Product> seller1Products = productService.getProductsBySeller(seller1);
        assertEquals(2, seller1Products.size());

        List<Product> seller2Products = productService.getProductsBySeller(seller2);
        assertTrue(seller2Products.isEmpty());
    }
}
