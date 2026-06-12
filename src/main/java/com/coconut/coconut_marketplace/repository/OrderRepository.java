package com.coconut.coconut_marketplace.repository;

import com.coconut.coconut_marketplace.entity.Order;
import com.coconut.coconut_marketplace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);
}
