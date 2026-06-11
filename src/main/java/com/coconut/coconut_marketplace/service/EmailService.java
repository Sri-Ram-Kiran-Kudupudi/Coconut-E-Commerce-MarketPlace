package com.coconut.coconut_marketplace.service;

import com.coconut.coconut_marketplace.exception.EmailSendException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Your Coconut Marketplace OTP");
            
            String text = "Hello,\n\n"
                    + "Your One-Time Password (OTP) for email verification is:\n\n"
                    + "        " + otp + "\n\n"
                    + "This OTP is valid for 5 minutes.\n"
                    + "Do not share this OTP with anyone.\n\n"
                    + "Team Coconut Marketplace";
            
            message.setText(text);
            mailSender.send(message);
        } catch (Exception e) {
            throw new EmailSendException("Failed to send OTP. Please try again.", e);
        }
    }
}
