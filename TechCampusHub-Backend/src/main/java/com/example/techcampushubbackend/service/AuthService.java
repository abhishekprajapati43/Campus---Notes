package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.config.AppProperties;
import com.example.techcampushubbackend.dto.AuthDtos.AuthResponse;
import com.example.techcampushubbackend.dto.AuthDtos.ChangePasswordRequest;
import com.example.techcampushubbackend.dto.AuthDtos.ForgotPasswordRequest;
import com.example.techcampushubbackend.dto.AuthDtos.LoginRequest;
import com.example.techcampushubbackend.dto.AuthDtos.RegisterRequest;
import com.example.techcampushubbackend.dto.AuthDtos.ResetPasswordRequest;
import com.example.techcampushubbackend.dto.AuthDtos.UpdateProfileRequest;
import com.example.techcampushubbackend.dto.AuthDtos.UserResponse;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ApiException;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ConflictException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.security.JwtService;
import com.example.techcampushubbackend.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * All authentication flows: register, login (JWT), e-mail verification,
 * forgot/reset password and profile updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int RESET_TOKEN_TTL_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AppProperties properties;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .phone(blankToNull(request.phone()))
                .role(Role.STUDENT)
                .emailVerified(false)
                .verificationToken(TokenUtils.randomToken())
                .enabled(true)
                .build();
        user = userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getName(),
                user.getVerificationToken());
        log.info("Registered student {}", email);
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
        if (properties.getSecurity().isRequireEmailVerification() && !user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Email not verified. Please check your inbox for the verification link.");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), toUserResponse(user));
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Verification token", "invalid"));
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);
        log.info("Verified email for {}", user.getEmail());
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        if (user.isEmailVerified()) {
            return; // silently succeed - do not leak account state
        }
        user.setVerificationToken(TokenUtils.randomToken());
        userRepository.save(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getName(),
                user.getVerificationToken());
    }

    /**
     * Always succeeds from the caller's perspective: responding with "unknown email"
     * would leak which addresses have accounts.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email().trim().toLowerCase()).ifPresent(user -> {
            user.setResetToken(TokenUtils.randomToken());
            user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(),
                    user.getResetToken());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .orElseThrow(() -> new BadRequestException("Reset link is invalid or has expired"));
        if (user.getResetTokenExpiresAt() == null
                || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset link is invalid or has expired");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        userRepository.save(user);
        log.info("Password reset for {}", user.getEmail());
    }

    @Transactional
    public UserResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.phone() != null) {
            user.setPhone(blankToNull(request.phone()));
        }
        return toUserResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return toUserResponse(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId)));
    }

    public static UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.isEmailVerified(), user.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
