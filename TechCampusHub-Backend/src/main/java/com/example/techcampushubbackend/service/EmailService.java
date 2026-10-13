package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional e-mails (verification, password reset).
 *
 * <p>When SMTP is not configured (no {@code spring.mail.host} / sender bean,
 * or sending fails), the message is logged to the console instead, so the auth
 * flows remain fully usable in development and tests.</p>
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties properties;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider, AppProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    public void sendVerificationEmail(String to, String name, String token) {
        String link = properties.getBaseUrl() + "/api/auth/verify-email?token=" + token;
        String subject = "Verify your TechCampus Hub email";
        String body = """
                Hi %s,

                Please verify your email address by opening the link below:

                %s

                If you did not create an account, you can ignore this message.

                - TechCampus Hub
                """.formatted(name, link);
        send(to, subject, body);
    }

    public void sendPasswordResetEmail(String to, String name, String token) {
        String link = properties.getBaseUrl() + "/api/auth/reset-password?token=" + token;
        String subject = "Reset your TechCampus Hub password";
        String body = """
                Hi %s,

                We received a request to reset your password. Open the link below
                within 30 minutes to choose a new one:

                %s

                If you did not request this, you can safely ignore this message.

                - TechCampus Hub
                """.formatted(name, link);
        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("No mail sender configured. Email to '{}' - '{}':\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getMail().getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Sent email '{}' to {}", subject, to);
        } catch (Exception ex) {
            log.warn("SMTP send failed ({}). Email to '{}' - '{}':\n{}",
                    ex.getMessage(), to, subject, body);
        }
    }
}
