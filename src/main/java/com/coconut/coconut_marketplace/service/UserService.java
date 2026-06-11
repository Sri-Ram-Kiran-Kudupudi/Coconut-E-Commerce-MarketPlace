package com.coconut.coconut_marketplace.service;

import com.coconut.coconut_marketplace.entity.SellerProfile;
import com.coconut.coconut_marketplace.entity.User;
import com.coconut.coconut_marketplace.enums.Role;
import com.coconut.coconut_marketplace.exception.OtpVerificationException;
import com.coconut.coconut_marketplace.repository.SellerProfileRepository;
import com.coconut.coconut_marketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Transactional
    public User registerBuyer(String fullName, String email, String password, String confirmPassword) {
        validatePasswordMatch(password, confirmPassword);
        validateOtpVerified(email);
        validateEmailUniqueness(email);

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.BUYER)
                .emailVerified(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User registerSeller(String fullName, String email, String storeName, String storeDescription, String password, String confirmPassword) {
        validatePasswordMatch(password, confirmPassword);
        validateOtpVerified(email);
        validateEmailUniqueness(email);

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.SELLER)
                .emailVerified(true)
                .build();

        User savedUser = userRepository.save(user);

        SellerProfile profile = SellerProfile.builder()
                .user(savedUser)
                .storeName(storeName)
                .storeDescription(storeDescription)
                .build();

        sellerProfileRepository.save(profile);

        return savedUser;
    }

    private void validatePasswordMatch(String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
    }

    private void validateOtpVerified(String email) {
        if (!otpService.isEmailVerified(email)) {
            throw new OtpVerificationException("Email verification required.");
        }
    }

    private void validateEmailUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
    }
}
