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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.io.File;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

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

    @Autowired
    private com.coconut.coconut_marketplace.repository.ProductRepository productRepository;

    // List of predefined product images under static folder
    private List<String> getDynamicImages() {
        Set<String> images = new LinkedHashSet<>();

        // 1. Try resolving from local development filesystem (to support instant reloading in local dev)
        String[] relativePaths = {
            "src/main/resources/static/css/images/products",
            "src/main/resources/static/images/products"
        };
        for (String relPath : relativePaths) {
            File dir = new File(relPath);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && isImageFile(file.getName())) {
                            String webPath = relPath.substring("src/main/resources/static".length()) + "/" + file.getName();
                            images.add(webPath.replace('\\', '/'));
                        }
                    }
                }
            }
        }

        // 2. Try resolving from classpath pattern (for standard Spring/production execution)
        String[] classpathPatterns = {
            "classpath:/static/css/images/products/*",
            "classpath:/static/images/products/*"
        };
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String pattern : classpathPatterns) {
            try {
                Resource[] resources = resolver.getResources(pattern);
                for (Resource resource : resources) {
                    String filename = resource.getFilename();
                    if (filename != null && isImageFile(filename)) {
                        String prefix = pattern.replace("classpath:/static", "").replace("*", "");
                        images.add(prefix + filename);
                    }
                }
            } catch (IOException e) {
                // Ignore pattern match failure
            }
        }

        // If nothing was found, return a default fallback list so the app doesn't break
        if (images.isEmpty()) {
            return Arrays.asList(
                "/css/images/products/coconut.png",
                "/css/images/products/tender_coconut.png",
                "/css/images/products/coconut_oil.png",
                "/css/images/products/coconut_water.png",
                "/css/images/products/coconut_milk.png",
                "/css/images/products/coconut_sapling.png"
            );
        }

        List<String> sortedImages = new ArrayList<>(images);
        Collections.sort(sortedImages);
        return sortedImages;
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") 
            || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg");
    }

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

        BigDecimal totalRevenue = orderItemRepository.sumRevenueBySellerAndStatusNot(seller, OrderStatus.CANCELLED);
        BigDecimal deliveredRevenue = orderItemRepository.sumDeliveredRevenueBySeller(seller);
        BigDecimal pendingRevenue = orderItemRepository.sumPendingRevenueBySeller(seller);

        long totalOrders = orderItemRepository.countDistinctOrdersBySeller(seller);
        long pendingOrders = orderItemRepository.countBySellerAndStatus(seller, OrderStatus.PENDING);
        long shippedOrders = orderItemRepository.countBySellerAndStatus(seller, OrderStatus.SHIPPED);
        long deliveredOrders = orderItemRepository.countBySellerAndStatus(seller, OrderStatus.DELIVERED);

        List<OrderItem> recentOrders = orderItemRepository.findTop5BySellerOrderByCreatedAtDesc(seller);
        List<Product> lowStockProducts = productRepository.findBySellerAndStockQuantityLessThanEqualAndStatusNotOrderByStockQuantityAsc(seller, 5, ProductStatus.DISABLED);

        model.addAttribute("seller", seller);
        model.addAttribute("products", products);
        model.addAttribute("totalProducts", productService.getProductCountBySeller(seller));
        model.addAttribute("activeProducts", productService.getActiveProductCountBySeller(seller));
        
        model.addAttribute("totalSales", "₹" + totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
        model.addAttribute("deliveredSales", "₹" + deliveredRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
        model.addAttribute("pendingSales", "₹" + pendingRevenue.setScale(2, java.math.RoundingMode.HALF_UP));
        
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("shippedOrders", shippedOrders);
        model.addAttribute("deliveredOrders", deliveredOrders);
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("lowStockProducts", lowStockProducts);

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
        model.addAttribute("images", getDynamicImages());
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
            model.addAttribute("images", getDynamicImages());
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
        model.addAttribute("images", getDynamicImages());
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
            model.addAttribute("images", getDynamicImages());
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
