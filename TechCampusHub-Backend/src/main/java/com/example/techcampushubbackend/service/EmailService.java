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
        // The reset endpoint is a POST that takes {token, newPassword}; there is
        // no frontend page that collects the token, so a bare GET link would be
        // unusable (it would only return 405). Emitting a copy-pasteable curl
        // keeps the flow completable from the e-mail alone, with no route changes.
        String resetCommand = "curl -X POST " + properties.getBaseUrl()
                + "/api/auth/reset-password -H \"Content-Type: application/json\" "
                + "-d '{\"token\":\"" + token + "\",\"newPassword\":\"YOUR_NEW_PASSWORD\"}'";
        String subject = "Reset your TechCampus Hub password";
        String body = """
                Hi %s,

                We received a request to reset your password. Run the command below
                within 30 minutes, replacing YOUR_NEW_PASSWORD with your new password:

                %s

                If you did not request this, you can safely ignore this message.

                - TechCampus Hub
                """.formatted(name, resetCommand);
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
            // The body carries one-time verification / reset links -
            // never write it to the logs when real SMTP delivery fails.
            log.warn("SMTP send failed ({}). Email to '{}' - '{}' was not delivered; message body withheld from logs.",
                    ex.getMessage(), to, subject);
        }
    }
}
