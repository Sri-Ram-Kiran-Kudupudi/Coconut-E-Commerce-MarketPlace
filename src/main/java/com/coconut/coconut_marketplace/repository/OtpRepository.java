package com.coconut.coconut_marketplace.repository;

import com.coconut.coconut_marketplace.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findFirstByEmailOrderByCreatedAtDesc(String email);
    Optional<OtpVerification> findFirstByEmailAndVerifiedTrueAndExpiresAtAfter(String email, LocalDateTime now);
    void deleteByEmail(String email);
}
