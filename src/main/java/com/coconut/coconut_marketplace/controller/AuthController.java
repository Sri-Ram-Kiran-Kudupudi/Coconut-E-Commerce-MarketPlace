package com.coconut.coconut_marketplace.controller;

import com.coconut.coconut_marketplace.service.OtpService;
import com.coconut.coconut_marketplace.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private OtpService otpService;

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String showLoginPage(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                @RequestParam(value = "expired", required = false) String expired,
                                Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid email or password.");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "You have been logged out successfully.");
        }
        if (expired != null) {
            model.addAttribute("expiredMessage", "Your session has expired. Please log in again.");
        }
        return "auth/login";
    }

    @GetMapping("/select-role")
    public String showSelectRolePage() {
        return "auth/select-role";
    }

    @GetMapping("/register/buyer")
    public String showBuyerRegistrationPage(Model model) {
        model.addAttribute("fullName", "");
        model.addAttribute("email", "");
        return "auth/register-buyer";
    }

    @PostMapping("/register/buyer")
    public String registerBuyer(@RequestParam String fullName,
                                @RequestParam String email,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        try {
            userService.registerBuyer(fullName, email, password, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Registration successful. Please log in.");
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("fullName", fullName);
            model.addAttribute("email", email);
            return "auth/register-buyer";
        }
    }

    @GetMapping("/register/seller")
    public String showSellerRegistrationPage(Model model) {
        model.addAttribute("fullName", "");
        model.addAttribute("email", "");
        model.addAttribute("storeName", "");
        model.addAttribute("storeDescription", "");
        return "auth/register-seller";
    }

    @PostMapping("/register/seller")
    public String registerSeller(@RequestParam String fullName,
                                 @RequestParam String email,
                                 @RequestParam String storeName,
                                 @RequestParam String storeDescription,
                                 @RequestParam String password,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        try {
            userService.registerSeller(fullName, email, storeName, storeDescription, password, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Registration successful. Please log in.");
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("fullName", fullName);
            model.addAttribute("email", email);
            model.addAttribute("storeName", storeName);
            model.addAttribute("storeDescription", storeDescription);
            return "auth/register-seller";
        }
    }

    @PostMapping("/otp/send")
    @ResponseBody
    public ResponseEntity<Map<String, String>> sendOtp(@RequestParam(value = "email", required = false) String email) {
        Map<String, String> response = new HashMap<>();
        if (email == null || email.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Please enter your email address first.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            otpService.generateAndSendOtp(email);
            response.put("status", "sent");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/otp/verify")
    @ResponseBody
    public ResponseEntity<Map<String, String>> verifyOtp(@RequestParam("email") String email,
                                                         @RequestParam("otp") String otp) {
        Map<String, String> response = new HashMap<>();
        if (email == null || email.trim().isEmpty() || otp == null || otp.trim().isEmpty()) {
            response.put("status", "invalid");
            response.put("message", "Email and OTP are required.");
            return ResponseEntity.badRequest().body(response);
        }

        String result = otpService.verifyOtp(email, otp);
        response.put("status", result);
        return ResponseEntity.ok(response);
    }
}
