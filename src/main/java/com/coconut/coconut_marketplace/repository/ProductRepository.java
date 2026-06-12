package com.coconut.coconut_marketplace.repository;

import com.coconut.coconut_marketplace.entity.Product;
import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.enums.Category;
import com.coconut.coconut_marketplace.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySeller(SellerProfile seller);
    List<Product> findBySellerOrderByCreatedAtDesc(SellerProfile seller);
    List<Product> findByStatus(ProductStatus status);
    List<Product> findByCategoryAndStatus(Category category, ProductStatus status);
    List<Product> findByStatusAndNameContainingIgnoreCase(ProductStatus status, String name);
    long countBySeller(SellerProfile seller);
    long countBySellerAndStatus(SellerProfile seller, ProductStatus status);
}
