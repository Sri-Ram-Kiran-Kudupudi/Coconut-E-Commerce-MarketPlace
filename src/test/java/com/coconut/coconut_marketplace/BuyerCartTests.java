package com.coconut.coconut_marketplace;

import com.coconut.coconut_marketplace.dto.CartItemDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuyerCartTests {

    @Autowired
    private MockMvc mockMvc;

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

    private User buyer;
    private Product activeProduct1;
    private Product activeProduct2;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        sellerProfileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Create Buyer
        buyer = User.builder()
                .fullName("Buyer Bob")
                .email("buyer@example.com")
                .password("password123")
                .role(Role.BUYER)
                .emailVerified(true)
                .build();
        userRepository.save(buyer);

        // Create Seller Profile
        User sellerUser = User.builder()
                .fullName("Seller Sam")
                .email("seller@example.com")
                .password("password123")
                .role(Role.SELLER)
                .emailVerified(true)
                .build();
        User savedSellerUser = userRepository.save(sellerUser);
        SellerProfile sellerProfile = sellerProfileRepository.save(SellerProfile.builder()
                .user(savedSellerUser)
                .storeName("Sam's Coconuts")
                .storeDescription("Selling natural products")
                .build());

        // Create Products
        activeProduct1 = Product.builder()
                .name("Tender Coconut")
                .description("Sweet tender coconut water.")
                .price(new BigDecimal("50.00"))
                .stockQuantity(10)
                .category(Category.FRESH_COCONUT)
                .imageUrl("/css/images/products/tender_coconut.png")
                .status(ProductStatus.ACTIVE)
                .seller(sellerProfile)
                .build();
        productRepository.save(activeProduct1);

        activeProduct2 = Product.builder()
                .name("Extra Virgin Coconut Oil")
                .description("Cold pressed pure virgin coconut oil.")
                .price(new BigDecimal("220.00"))
                .stockQuantity(5)
                .category(Category.COCONUT_OIL)
                .imageUrl("/css/images/products/coconut_oil.png")
                .status(ProductStatus.ACTIVE)
                .seller(sellerProfile)
                .build();
        productRepository.save(activeProduct2);
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void testBuyerDashboard_NoFilters() throws Exception {
        mockMvc.perform(get("/buyer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("products"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(view().name("buyer/dashboard"));
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void testBuyerDashboard_SearchFilter() throws Exception {
        mockMvc.perform(get("/buyer/dashboard")
                        .param("search", "Oil"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("products"))
                .andExpect(view().name("buyer/dashboard"));
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    void testViewProductDetails() throws Exception {
        mockMvc.perform(get("/buyer/product/" + activeProduct1.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("product"))
                .andExpect(view().name("buyer/product-details"));
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    @SuppressWarnings("unchecked")
    void testAddToCart_Success() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/buyer/cart/add")
                        .param("productId", activeProduct1.getId().toString())
                        .param("quantity", "2")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/buyer/cart"))
                .andExpect(flash().attributeExists("successMessage"));

        // Verify session data
        Map<Long, CartItemDto> cart = (Map<Long, CartItemDto>) session.getAttribute("sessionCart");
        assertNotNull(cart);
        assertTrue(cart.containsKey(activeProduct1.getId()));
        assertEquals(2, cart.get(activeProduct1.getId()).getQuantity());
        assertEquals(2, session.getAttribute("cartCount"));
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    @SuppressWarnings("unchecked")
    void testUpdateCartQuantity() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Add to cart first
        mockMvc.perform(post("/buyer/cart/add")
                        .param("productId", activeProduct1.getId().toString())
                        .param("quantity", "2")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 2. Perform update
        mockMvc.perform(post("/buyer/cart/update")
                        .param("productId", activeProduct1.getId().toString())
                        .param("quantity", "5")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/buyer/cart"));

        // Verify session updates
        Map<Long, CartItemDto> cart = (Map<Long, CartItemDto>) session.getAttribute("sessionCart");
        assertNotNull(cart);
        assertEquals(5, cart.get(activeProduct1.getId()).getQuantity());
        assertEquals(5, session.getAttribute("cartCount"));
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "BUYER")
    @SuppressWarnings("unchecked")
    void testRemoveFromCart() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Add to cart
        mockMvc.perform(post("/buyer/cart/add")
                        .param("productId", activeProduct1.getId().toString())
                        .param("quantity", "1")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 2. Perform remove
        mockMvc.perform(post("/buyer/cart/remove/" + activeProduct1.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/buyer/cart"))
                .andExpect(flash().attributeExists("successMessage"));

        // Verify removal in session
        Map<Long, CartItemDto> cart = (Map<Long, CartItemDto>) session.getAttribute("sessionCart");
        assertNotNull(cart);
        assertFalse(cart.containsKey(activeProduct1.getId()));
        assertEquals(0, session.getAttribute("cartCount"));
    }
}
