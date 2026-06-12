package com.coconut.coconut_marketplace.service;

import com.coconut.coconut_marketplace.entity.Product;
import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.enums.ProductStatus;
import com.coconut.coconut_marketplace.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public List<Product> getProductsBySeller(SellerProfile seller) {
        return productRepository.findBySellerOrderByCreatedAtDesc(seller);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + id));
    }

    public Product getProductByIdAndSeller(Long id, SellerProfile seller) {
        Product product = getProductById(id);
        validateOwnership(product, seller);
        return product;
    }

    @Transactional
    public Product createProduct(Product product, SellerProfile seller) {
        product.setSeller(seller);
        validateProduct(product);
        autoAssignStatus(product);
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long productId, Product updatedProduct, SellerProfile seller) {
        Product existingProduct = getProductById(productId);
        validateOwnership(existingProduct, seller);

        existingProduct.setName(updatedProduct.getName());
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setStockQuantity(updatedProduct.getStockQuantity());
        existingProduct.setCategory(updatedProduct.getCategory());
        existingProduct.setImageUrl(updatedProduct.getImageUrl());

        // Manage status transitions
        if (updatedProduct.getStatus() == ProductStatus.DISABLED) {
            existingProduct.setStatus(ProductStatus.DISABLED);
        } else {
            // Default to ACTIVE / OUT_OF_STOCK based on stock
            autoAssignStatus(existingProduct);
        }

        validateProduct(existingProduct);
        return productRepository.save(existingProduct);
    }

    @Transactional
    public void deleteProduct(Long productId, SellerProfile seller) {
        Product product = getProductById(productId);
        validateOwnership(product, seller);
        productRepository.delete(product);
    }

    public long getProductCountBySeller(SellerProfile seller) {
        return productRepository.countBySeller(seller);
    }

    public long getActiveProductCountBySeller(SellerProfile seller) {
        return productRepository.countBySellerAndStatus(seller, ProductStatus.ACTIVE);
    }

    private void validateOwnership(Product product, SellerProfile seller) {
        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new SecurityException("You do not have permission to modify this product.");
        }
    }

    private void autoAssignStatus(Product product) {
        if (product.getStatus() == ProductStatus.DISABLED) {
            return; // Maintain manually disabled status
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() <= 0) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        } else {
            product.setStatus(ProductStatus.ACTIVE);
        }
    }

    private void validateProduct(Product product) {
        if (product.getName() == null || product.getName().trim().length() < 2 || product.getName().length() > 150) {
            throw new IllegalArgumentException("Product name must be between 2 and 150 characters.");
        }
        if (product.getDescription() == null || product.getDescription().trim().length() < 10) {
            throw new IllegalArgumentException("Description must be at least 10 characters long.");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0.");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative.");
        }
        if (product.getImageUrl() == null || product.getImageUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Product image selection is required.");
        }
    }
}
