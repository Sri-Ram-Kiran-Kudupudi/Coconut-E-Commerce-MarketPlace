package com.coconut.coconut_marketplace.controller;

import com.coconut.coconut_marketplace.dto.CartItemDto;
import com.coconut.coconut_marketplace.entity.Product;
import com.coconut.coconut_marketplace.entity.User;
import com.coconut.coconut_marketplace.entity.Order;
import com.coconut.coconut_marketplace.entity.OrderItem;
import com.coconut.coconut_marketplace.enums.Category;
import com.coconut.coconut_marketplace.enums.ProductStatus;
import com.coconut.coconut_marketplace.repository.ProductRepository;
import com.coconut.coconut_marketplace.repository.UserRepository;
import com.coconut.coconut_marketplace.repository.OrderRepository;
import com.coconut.coconut_marketplace.service.ProductService;
import com.coconut.coconut_marketplace.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/buyer")
public class BuyerController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    private static final String CART_SESSION_KEY = "sessionCart";

    private User getLoggedInUser(Principal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication is required.");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, CartItemDto> getCartFromSession(HttpSession session) {
        Map<Long, CartItemDto> cart = (Map<Long, CartItemDto>) session.getAttribute(CART_SESSION_KEY);
        if (cart == null) {
            cart = new HashMap<>();
            session.setAttribute(CART_SESSION_KEY, cart);
        }
        return cart;
    }

    private void updateSessionCartCount(HttpSession session, Map<Long, CartItemDto> cart) {
        int totalItems = cart.values().stream().mapToInt(CartItemDto::getQuantity).sum();
        session.setAttribute("cartCount", totalItems);
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "search", required = false) String search,
                            @RequestParam(value = "category", required = false) Category category,
                            Model model,
                            Principal principal) {
        getLoggedInUser(principal); // Security check
        
        List<Product> products;

        if (category != null && search != null && !search.trim().isEmpty()) {
            products = productRepository.findByStatusAndCategoryAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                    ProductStatus.ACTIVE, category, search.trim());
        } else if (category != null) {
            products = productRepository.findByCategoryAndStatusOrderByCreatedAtDesc(category, ProductStatus.ACTIVE);
        } else if (search != null && !search.trim().isEmpty()) {
            products = productRepository.findByStatusAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                    ProductStatus.ACTIVE, search.trim());
        } else {
            products = productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.ACTIVE);
        }

        model.addAttribute("products", products);
        model.addAttribute("categories", Category.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("searchKeyword", search);
        return "buyer/dashboard";
    }

    @GetMapping("/product/{id}")
    public String viewProductDetails(@PathVariable Long id, Model model, Principal principal) {
        getLoggedInUser(principal); // Security check
        Product product = productService.getProductById(id);

        if (product.getStatus() == ProductStatus.DISABLED) {
            throw new SecurityException("This product listing is currently unavailable.");
        }

        model.addAttribute("product", product);
        return "buyer/product-details";
    }

    // ==========================================
    // CART OPERATIONS
    // ==========================================

    @GetMapping("/cart")
    public String viewCart(Model model, Principal principal, HttpSession session) {
        getLoggedInUser(principal);
        Map<Long, CartItemDto> cart = getCartFromSession(session);
        
        BigDecimal subtotal = cart.values().stream()
                .map(CartItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("cartItems", new ArrayList<>(cart.values()));
        model.addAttribute("cartSubtotal", subtotal);
        return "buyer/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam("productId") Long productId,
                            @RequestParam("quantity") Integer quantity,
                            HttpSession session,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        try {
            getLoggedInUser(principal);
            Product product = productService.getProductById(productId);

            if (product.getStatus() != ProductStatus.ACTIVE) {
                redirectAttributes.addFlashAttribute("error", "Product is currently out of stock or unavailable.");
                return "redirect:/buyer/dashboard";
            }

            if (quantity <= 0) {
                redirectAttributes.addFlashAttribute("error", "Quantity must be at least 1.");
                return "redirect:/buyer/product/" + productId;
            }

            if (quantity > product.getStockQuantity()) {
                redirectAttributes.addFlashAttribute("error", "Cannot add more than available stock (" + product.getStockQuantity() + ").");
                return "redirect:/buyer/product/" + productId;
            }

            Map<Long, CartItemDto> cart = getCartFromSession(session);
            
            if (cart.containsKey(productId)) {
                CartItemDto existingItem = cart.get(productId);
                int newQty = existingItem.getQuantity() + quantity;
                if (newQty > product.getStockQuantity()) {
                    existingItem.setQuantity(product.getStockQuantity());
                    redirectAttributes.addFlashAttribute("infoMessage", "Capped item quantity at maximum available stock.");
                } else {
                    existingItem.setQuantity(newQty);
                }
            } else {
                CartItemDto newItem = CartItemDto.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .imageUrl(product.getImageUrl())
                        .categoryDisplayName(product.getCategory().getDisplayName())
                        .price(product.getPrice())
                        .quantity(quantity)
                        .storeName(product.getSeller().getStoreName())
                        .stockQuantity(product.getStockQuantity())
                        .build();
                cart.put(productId, newItem);
            }

            updateSessionCartCount(session, cart);
            redirectAttributes.addFlashAttribute("successMessage", "Item added to cart successfully.");
            return "redirect:/buyer/cart";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/buyer/dashboard";
        }
    }

    @PostMapping("/cart/update")
    public String updateCartQuantity(@RequestParam("productId") Long productId,
                                     @RequestParam("quantity") Integer quantity,
                                     HttpSession session,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            getLoggedInUser(principal);
            Map<Long, CartItemDto> cart = getCartFromSession(session);

            if (!cart.containsKey(productId)) {
                return "redirect:/buyer/cart";
            }

            if (quantity <= 0) {
                cart.remove(productId);
            } else {
                Product product = productService.getProductById(productId);
                CartItemDto item = cart.get(productId);
                if (quantity > product.getStockQuantity()) {
                    item.setQuantity(product.getStockQuantity());
                    redirectAttributes.addFlashAttribute("error", "Requested quantity exceeds available stock (" + product.getStockQuantity() + "). Quantity set to maximum.");
                } else {
                    item.setQuantity(quantity);
                }
            }

            updateSessionCartCount(session, cart);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/buyer/cart";
    }

    @PostMapping("/cart/remove/{id}")
    public String removeFromCart(@PathVariable Long id,
                                 HttpSession session,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            getLoggedInUser(principal);
            Map<Long, CartItemDto> cart = getCartFromSession(session);
            
            if (cart.containsKey(id)) {
                cart.remove(id);
                redirectAttributes.addFlashAttribute("successMessage", "Item removed from cart.");
            }
            
            updateSessionCartCount(session, cart);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/buyer/cart";
    }

    // ==========================================
    // BUYER PROFILE & ORDERS
    // ==========================================

    @GetMapping("/profile")
    public String showProfile(Model model, Principal principal) {
        User user = getLoggedInUser(principal);
        model.addAttribute("user", user);
        model.addAttribute("stats", orderService.getBuyerStats(user));
        return "buyer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute User userDetails,
                                Principal principal,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        try {
            User currentUser = getLoggedInUser(principal);
            currentUser.setFullName(userDetails.getFullName());
            currentUser.setPhoneNumber(userDetails.getPhoneNumber());
            currentUser.setAddress(userDetails.getAddress());

            userRepository.save(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
            return "redirect:/buyer/profile";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("user", userDetails);
            return "buyer/profile";
        }
    }

    @GetMapping("/checkout")
    public String viewCheckout(Model model, Principal principal, HttpSession session) {
        User user = getLoggedInUser(principal);
        Map<Long, CartItemDto> cart = getCartFromSession(session);
        if (cart.isEmpty()) {
            return "redirect:/buyer/cart";
        }

        BigDecimal subtotal = cart.values().stream()
                .map(CartItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deliveryCharge = subtotal.compareTo(BigDecimal.valueOf(500)) >= 0 
                ? BigDecimal.ZERO 
                : BigDecimal.valueOf(50);
        BigDecimal totalAmount = subtotal.add(deliveryCharge);

        List<Order> pastOrders = orderRepository.findByBuyerOrderByCreatedAtDesc(user);
        Order lastOrder = pastOrders.isEmpty() ? null : pastOrders.get(0);

        Order shippingInfo = Order.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(lastOrder != null ? lastOrder.getPhoneNumber() : (user.getPhoneNumber() != null ? user.getPhoneNumber() : ""))
                .address(lastOrder != null ? lastOrder.getAddress() : (user.getAddress() != null ? user.getAddress() : ""))
                .city(lastOrder != null ? lastOrder.getCity() : "")
                .district(lastOrder != null ? lastOrder.getDistrict() : "")
                .state(lastOrder != null ? lastOrder.getState() : "")
                .pincode(lastOrder != null ? lastOrder.getPincode() : "")
                .build();

        model.addAttribute("cartItems", new ArrayList<>(cart.values()));
        model.addAttribute("cartSubtotal", subtotal);
        model.addAttribute("deliveryCharge", deliveryCharge);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("shippingInfo", shippingInfo);
        
        return "buyer/checkout";
    }

    @PostMapping("/checkout")
    public String processCheckout(@ModelAttribute("shippingInfo") Order shippingInfo,
                                  HttpSession session,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            User buyer = getLoggedInUser(principal);
            Map<Long, CartItemDto> cart = getCartFromSession(session);
            if (cart.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Your cart is empty. Cannot checkout.");
                return "redirect:/buyer/cart";
            }

            // Persist shipping information back to the buyer's profile
            buyer.setPhoneNumber(shippingInfo.getPhoneNumber());
            buyer.setAddress(shippingInfo.getAddress());
            if (shippingInfo.getFullName() != null && !shippingInfo.getFullName().trim().isEmpty()) {
                buyer.setFullName(shippingInfo.getFullName());
            }
            userRepository.save(buyer);

            Order placedOrder = orderService.createOrderFromCart(buyer, new ArrayList<>(cart.values()), shippingInfo);

            // Success, clear session cart
            session.removeAttribute(CART_SESSION_KEY);
            session.setAttribute("cartCount", 0);

            redirectAttributes.addFlashAttribute("successMessage", "Order placed successfully! Order Number: " + placedOrder.getOrderNumber());
            return "redirect:/buyer/orders";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/buyer/checkout";
        }
    }

    @GetMapping("/orders")
    public String showOrders(Model model, Principal principal) {
        User buyer = getLoggedInUser(principal);
        List<Order> orders = orderService.getOrdersByBuyer(buyer);
        model.addAttribute("orders", orders);
        model.addAttribute("stats", orderService.getBuyerStats(buyer));
        return "buyer/orders";
    }

    @GetMapping("/order/{id}")
    public String viewOrderDetails(@PathVariable("id") Long id, Model model, Principal principal) {
        User buyer = getLoggedInUser(principal);
        Order order = orderService.getOrderById(id);
        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new SecurityException("You do not have permission to view this order.");
        }
        model.addAttribute("order", order);
        return "buyer/order-details";
    }

    @PostMapping("/orders/cancel/{itemId}")
    public String cancelOrderItem(@PathVariable("itemId") Long itemId,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        Long orderId = null;
        try {
            User buyer = getLoggedInUser(principal);
            OrderItem item = orderService.getOrderItemById(itemId);
            orderId = item.getOrder().getId();

            orderService.cancelItemByBuyer(itemId, buyer);
            redirectAttributes.addFlashAttribute("successMessage", "Order item cancelled successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        if (orderId != null) {
            return "redirect:/buyer/order/" + orderId;
        }
        return "redirect:/buyer/orders";
    }

    @PostMapping("/orders/confirm-delivery/{itemId}")
    public String confirmOrderItemDelivery(@PathVariable("itemId") Long itemId,
                                           Principal principal,
                                           RedirectAttributes redirectAttributes) {
        Long orderId = null;
        try {
            User buyer = getLoggedInUser(principal);
            OrderItem item = orderService.getOrderItemById(itemId);
            orderId = item.getOrder().getId();

            orderService.confirmDeliveryByBuyer(itemId, buyer);
            redirectAttributes.addFlashAttribute("successMessage", "Delivery confirmed successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        if (orderId != null) {
            return "redirect:/buyer/order/" + orderId;
        }
        return "redirect:/buyer/orders";
    }
}
