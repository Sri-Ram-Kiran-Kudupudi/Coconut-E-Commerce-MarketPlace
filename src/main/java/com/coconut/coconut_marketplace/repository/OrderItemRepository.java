package com.coconut.coconut_marketplace.repository;

import com.coconut.coconut_marketplace.entity.OrderItem;
import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findBySellerOrderByCreatedAtDesc(SellerProfile seller);

    List<OrderItem> findTop5BySellerOrderByCreatedAtDesc(SellerProfile seller);

    long countBySellerAndStatus(SellerProfile seller, OrderStatus status);

    @Query("SELECT COUNT(DISTINCT oi.order) FROM OrderItem oi WHERE oi.seller = :seller")
    long countDistinctOrdersBySeller(@Param("seller") SellerProfile seller);

    @Query("SELECT COALESCE(SUM(oi.totalPrice), 0) FROM OrderItem oi WHERE oi.seller = :seller AND oi.status != :status")
    BigDecimal sumRevenueBySellerAndStatusNot(@Param("seller") SellerProfile seller, @Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(oi.totalPrice), 0) FROM OrderItem oi WHERE oi.seller = :seller AND oi.status = com.coconut.coconut_marketplace.enums.OrderStatus.DELIVERED")
    BigDecimal sumDeliveredRevenueBySeller(@Param("seller") SellerProfile seller);

    @Query("SELECT COALESCE(SUM(oi.totalPrice), 0) FROM OrderItem oi WHERE oi.seller = :seller AND oi.status IN (com.coconut.coconut_marketplace.enums.OrderStatus.PENDING, com.coconut.coconut_marketplace.enums.OrderStatus.CONFIRMED, com.coconut.coconut_marketplace.enums.OrderStatus.SHIPPED)")
    BigDecimal sumPendingRevenueBySeller(@Param("seller") SellerProfile seller);
}
