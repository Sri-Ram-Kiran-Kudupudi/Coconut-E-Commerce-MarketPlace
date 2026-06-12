package com.coconut.coconut_marketplace.controller;

import com.coconut.coconut_marketplace.entity.Product;
import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.entity.User;
import com.coconut.coconut_marketplace.entity.OrderItem;
import com.coconut.coconut_marketplace.enums.Category;
import com.coconut.coconut_marketplace.enums.ProductStatus;
import com.coconut.coconut_marketplace.enums.OrderStatus;
import com.coconut.coconut_marketplace.repository.SellerProfileRepository;
import com.coconut.coconut_marketplace.repository.UserRepository;
import com.coconut.coconut_marketplace.repository.OrderItemRepository;
import com.coconut.coconut_marketplace.service.ProductService;
import com.coconut.coconut_marketplace.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/seller")
public class SellerController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemRepository orderItemRepository;

    // List of predefined product images under static folder
    private static final List<String> PREDEFINED_IMAGES = Arrays.asList(
            "/css/images/products/coconut.png",
            "/css/images/products/tender_coconut.png",
            "/css/images/products/coconut_flesh.png",
            "/css/images/products/sprouted_coconut.png",
            "/css/images/products/coconut_oil.png",
            "/css/images/products/coconut_water.png",
            "/css/images/products/coconut_milk.png",
            "/css/images/products/coconut_sapling.png",
            "/css/images/products/hybrid_coconut_sapling..png",
            "/css/images/products/coir_rope.png",
            "/css/images/products/coconut_husk.png",
            "/css/images/products/coconut_broom.png",
            "/css/images/products/coconut_shell.png",
            "/css/images/products/coconut_shell_charcoal.png",
            "/css/images/products/coconut_flower.png"
    );

    private SellerProfile getLoggedInSeller(Principal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication is required.");
        }
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

        return sellerProfileRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Seller profile not found for user: " + user.getEmail()));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        SellerProfile seller = getLoggedInSeller(principal);
        List<Product> products = productService.getProductsBySeller(seller);

        long totalOrders = orderItemRepository.countDistinctOrdersBySeller(seller);
        BigDecimal totalSales = orderItemRepository.sumRevenueBySellerAndStatusNot(seller, OrderStatus.CANCELLED);

        model.addAttribute("seller", seller);
        model.addAttribute("products", products);
        model.addAttribute("totalProducts", productService.getProductCountBySeller(seller));
        model.addAttribute("activeProducts", productService.getActiveProductCountBySeller(seller));
        model.addAttribute("totalSales", "₹" + totalSales.setScale(2, java.math.RoundingMode.HALF_UP));
        model.addAttribute("totalOrders", totalOrders);

        return "seller/dashboard";
    }

    @GetMapping("/products")
    public String viewProducts(Model model, Principal principal) {
        SellerProfile seller = getLoggedInSeller(principal);
        List<Product> products = productService.getProductsBySeller(seller);
        model.addAttribute("products", products);
        return "seller/products";
    }

    @GetMapping("/products/new")
    public String showAddProductForm(Model model, Principal principal) {
        getLoggedInSeller(principal); // Verify user is a seller
        
        model.addAttribute("product", new Product());
        model.addAttribute("categories", Category.values());
        model.addAttribute("images", PREDEFINED_IMAGES);
        model.addAttribute("isEdit", false);
        return "seller/product-form";
    }

    @PostMapping("/products/new")
    public String addProduct(@ModelAttribute Product product,
                             Principal principal,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        try {
            SellerProfile seller = getLoggedInSeller(principal);
            productService.createProduct(product, seller);
            redirectAttributes.addFlashAttribute("successMessage", "Product added successfully.");
            return "redirect:/seller/products";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("product", product);
            model.addAttribute("categories", Category.values());
            model.addAttribute("images", PREDEFINED_IMAGES);
            model.addAttribute("isEdit", false);
            return "seller/product-form";
        }
    }

    @GetMapping("/products/edit/{id}")
    public String showEditProductForm(@PathVariable Long id, Model model, Principal principal) {
        SellerProfile seller = getLoggedInSeller(principal);
        Product product = productService.getProductByIdAndSeller(id, seller);

        model.addAttribute("product", product);
        model.addAttribute("categories", Category.values());
        model.addAttribute("images", PREDEFINED_IMAGES);
        model.addAttribute("isEdit", true);
        return "seller/product-form";
    }

    @PostMapping("/products/edit/{id}")
    public String editProduct(@PathVariable Long id,
                              @ModelAttribute Product product,
                              Principal principal,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        try {
            SellerProfile seller = getLoggedInSeller(principal);
            productService.updateProduct(id, product, seller);
            redirectAttributes.addFlashAttribute("successMessage", "Product updated successfully.");
            return "redirect:/seller/products";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("product", product);
            model.addAttribute("categories", Category.values());
            model.addAttribute("images", PREDEFINED_IMAGES);
            model.addAttribute("isEdit", true);
            return "seller/product-form";
        }
    }

    @PostMapping("/products/delete/{id}")
    public String deleteProduct(@PathVariable Long id,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            SellerProfile seller = getLoggedInSeller(principal);
            productService.deleteProduct(id, seller);
            redirectAttributes.addFlashAttribute("successMessage", "Product deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/seller/products";
    }

    @GetMapping("/profile")
    public String showProfileForm(Model model, Principal principal) {
        SellerProfile seller = getLoggedInSeller(principal);
        model.addAttribute("seller", seller);
        return "seller/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute SellerProfile sellerDetails,
                                Principal principal,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        try {
            SellerProfile currentSeller = getLoggedInSeller(principal);
            currentSeller.setStoreName(sellerDetails.getStoreName());
            currentSeller.setStoreDescription(sellerDetails.getStoreDescription());
            currentSeller.setAddress(sellerDetails.getAddress());
            currentSeller.setCity(sellerDetails.getCity());
            currentSeller.setDistrict(sellerDetails.getDistrict());
            currentSeller.setState(sellerDetails.getState());
            currentSeller.setPincode(sellerDetails.getPincode());

            sellerProfileRepository.save(currentSeller);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
            return "redirect:/seller/profile";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("seller", sellerDetails);
            return "seller/profile";
        }
    }

    @GetMapping("/orders")
    public String viewOrders(Model model, Principal principal) {
        SellerProfile seller = getLoggedInSeller(principal);
        List<OrderItem> orderItems = orderService.getOrderItemsBySeller(seller);
        model.addAttribute("orderItems", orderItems);
        return "seller/orders";
    }

    @PostMapping("/orders/status/{itemId}")
    public String updateOrderStatus(@PathVariable("itemId") Long itemId,
                                    @RequestParam("status") OrderStatus status,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            SellerProfile seller = getLoggedInSeller(principal);
            orderService.updateItemStatus(itemId, status, seller);
            redirectAttributes.addFlashAttribute("successMessage", "Order item status updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/seller/orders";
    }
}
