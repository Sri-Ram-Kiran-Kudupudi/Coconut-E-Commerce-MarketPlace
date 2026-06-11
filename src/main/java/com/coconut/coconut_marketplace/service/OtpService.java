package com.coconut.coconut_marketplace.service;

import com.coconut.coconut_marketplace.entity.OtpVerification;
import com.coconut.coconut_marketplace.repository.OtpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private EmailService emailService;

    @Transactional
    public void generateAndSendOtp(String email) {
        // Invalidate/remove previous OTP records for this email
        otpRepository.deleteByEmail(email);

        // Generate 6-digit numeric OTP
        String otp = String.format("%06d", new Random().nextInt(1000000));

        // Create and save new verification record
        OtpVerification verification = OtpVerification.builder()
                .email(email)
                .otp(otp)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .build();
        otpRepository.save(verification);

        // Send OTP email
        emailService.sendOtpEmail(email, otp);
    }

    @Transactional
    public String verifyOtp(String email, String otpCode) {
        Optional<OtpVerification> optionalVerification = otpRepository.findFirstByEmailOrderByCreatedAtDesc(email);
        
        if (optionalVerification.isEmpty()) {
            return "invalid";
        }

        OtpVerification verification = optionalVerification.get();

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "expired";
        }

        if (verification.getOtp().equals(otpCode)) {
            verification.setVerified(true);
            otpRepository.save(verification);
            return "verified";
        } else {
            return "invalid";
        }
    }

    public boolean isEmailVerified(String email) {
        return otpRepository.findFirstByEmailAndVerifiedTrueAndExpiresAtAfter(email, LocalDateTime.now()).isPresent();
    }
}
