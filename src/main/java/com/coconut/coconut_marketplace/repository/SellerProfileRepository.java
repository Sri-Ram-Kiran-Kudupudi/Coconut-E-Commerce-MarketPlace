package com.coconut.coconut_marketplace.repository;

import com.coconut.coconut_marketplace.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {
}
