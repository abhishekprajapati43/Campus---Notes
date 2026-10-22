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
import com.example.techcampushubbackend.entity.RegistrationStatus;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ApiException;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ConflictException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CertificateRepository;
import com.example.techcampushubbackend.repository.FavoriteRepository;
import com.example.techcampushubbackend.repository.FeedbackRepository;
import com.example.techcampushubbackend.repository.NotificationRepository;
import com.example.techcampushubbackend.repository.PurchaseRepository;
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
import java.util.List;

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
    private final PurchaseRepository purchaseRepository;
    private final FavoriteRepository favoriteRepository;
    private final FeedbackRepository feedbackRepository;
    private final CertificateRepository certificateRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        Role role = resolveRegistrationRole(request.role());
        String phone = blankToNull(request.phone());
        // Address is stored for every role (students can enter one at sign-up);
        // only faculty are required to provide it.
        String address = blankToNull(request.address());
        String aadhaarHash = null;
        boolean declarationAccepted = false;

        if (role == Role.FACULTY) {
            // Defense in depth beyond the DTO constraints: every faculty-only
            // requirement is re-checked here so no code path can bypass it.
            if (phone == null || !phone.matches("\\d{10}")) {
                throw new BadRequestException("Faculty registration requires a 10-digit phone number");
            }
            if (request.aadhaarNumber() == null || !request.aadhaarNumber().trim().matches("\\d{12}")) {
                throw new BadRequestException("Faculty registration requires a 12-digit aadhaarNumber");
            }
            if (address == null) {
                throw new BadRequestException("Faculty registration requires an address");
            }
            if (!Boolean.TRUE.equals(request.declarationAccepted())) {
                throw new BadRequestException(
                        "Faculty registration requires accepting the declaration (declarationAccepted must be true)");
            }
            aadhaarHash = sha256Hex(request.aadhaarNumber().trim());
            declarationAccepted = true;
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .phone(phone)
                .role(role)
                .course(blankToNull(request.course()))
                .branch(blankToNull(request.branch()))
                .year(request.year())
                .semester(request.semester())
                .aadhaarNumber(aadhaarHash)
                .address(address)
                // Optional profile details shown on the profile pages; all
                // nullable so existing sign-up flows keep working unchanged.
                .gender(blankToNull(request.gender()))
                .enrollmentNumber(blankToNull(request.enrollmentNumber()))
                .department(blankToNull(request.department()))
                .qualification(blankToNull(request.qualification()))
                .experience(blankToNull(request.experience()))
                .declarationAccepted(declarationAccepted)
                // New faculty accounts stay locked until an administrator
                // approves them from the Manage Faculty panel.
                .registrationStatus(role == Role.FACULTY
                        ? RegistrationStatus.PENDING
                        : RegistrationStatus.APPROVED)
                .emailVerified(false)
                .verificationToken(TokenUtils.randomToken())
                .enabled(true)
                .build();
        user = userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getName(),
                user.getVerificationToken());
        log.info("Registered {} account {}", role, email);
        return toUserResponse(user);
    }

    /**
     * Self-registration may only ever produce STUDENT or FACULTY. Anything else
     * (notably ADMIN) is rejected - admins are seeded, never registered.
     */
    private static Role resolveRegistrationRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return Role.STUDENT;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (Role.STUDENT.name().equals(normalized) || Role.FACULTY.name().equals(normalized)) {
            return Role.valueOf(normalized);
        }
        throw new BadRequestException("role must be student or faculty");
    }

    /**
     * SHA-256 hex hash (lowercase, 64 chars) of the raw Aadhaar number. Only the
     * digest is persisted - the raw value never leaves this method, is never
     * logged and is never returned by any endpoint.
     */
    private static String sha256Hex(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                    .formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
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
        // Registration moderation runs before anything else so a pending or
        // rejected applicant gets an actionable message instead of a generic one.
        RegistrationStatus status = user.registrationStatusOrDefault();
        if (status == RegistrationStatus.PENDING) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Your faculty registration is pending approval. Please wait for an administrator to review it.");
        }
        if (status == RegistrationStatus.REJECTED) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Your registration was rejected by an administrator.");
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
        // Non-enumerating: the controller always answers "if the account
        // exists...", so an unknown address must not throw 404 and leak
        // which e-mails are registered. Mirrors forgotPassword() below.
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            if (user.isEmailVerified()) {
                return; // silently succeed - do not leak account state
            }
            user.setVerificationToken(TokenUtils.randomToken());
            userRepository.save(user);
            emailService.sendVerificationEmail(user.getEmail(), user.getName(),
                    user.getVerificationToken());
        });
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
        if (request.course() != null) {
            user.setCourse(blankToNull(request.course()));
        }
        if (request.branch() != null) {
            user.setBranch(blankToNull(request.branch()));
        }
        if (request.year() != null) {
            user.setYear(request.year());
        }
        if (request.semester() != null) {
            user.setSemester(request.semester());
        }
        // Faculty profile details edited from the faculty dashboard.
        // facultyId and email are deliberately never editable here.
        if (request.department() != null) {
            user.setDepartment(blankToNull(request.department()));
        }
        if (request.qualification() != null) {
            user.setQualification(blankToNull(request.qualification()));
        }
        if (request.experience() != null) {
            user.setExperience(blankToNull(request.experience()));
        }
        if (request.profilePhotoUrl() != null) {
            user.setProfilePhotoUrl(blankToNull(request.profilePhotoUrl()));
        }
        return toUserResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return toUserResponse(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId)));
    }

    /** Every registered account, newest first - powers the admin Users panel. */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AuthService::toUserResponse)
                .toList();
    }

    /**
     * Admin "Remove User" (suspend) / "Restore User": a suspended account keeps
     * its data but cannot log in until it is re-enabled.
     */
    @Transactional
    public UserResponse setUserEnabled(Long userId, boolean enabled, Long requestedByUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (enabled) {
            user.setEnabled(true);
            userRepository.save(user);
            log.info("Re-enabled account {} (by {})", user.getEmail(), requestedByUserId);
            return toUserResponse(user);
        }

        // Suspension guards: an admin cannot lock themselves out, and the last
        // enabled admin must stay in place so the platform remains manageable.
        if (userId.equals(requestedByUserId)) {
            throw new BadRequestException("You cannot suspend your own account");
        }
        if (user.getRole() == Role.ADMIN
                && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BadRequestException("The last enabled admin account cannot be suspended");
        }

        user.setEnabled(false);
        userRepository.save(user);
        log.info("Suspended account {} (by {})", user.getEmail(), requestedByUserId);
        return toUserResponse(user);
    }

    /**
     * Admin "Manage Faculty": approve or reject a faculty registration. Only
     * FACULTY accounts can go through this workflow; PENDING is never a value
     * an admin can set (only the registration flow creates pending accounts).
     */
    @Transactional
    public UserResponse setRegistrationStatus(Long userId, RegistrationStatus status,
                                               Long requestedByUserId) {
        if (status == null || status == RegistrationStatus.PENDING) {
            throw new BadRequestException("status must be APPROVED or REJECTED");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getRole() != Role.FACULTY) {
            throw new BadRequestException("Only faculty accounts can be approved or rejected");
        }

        user.setRegistrationStatus(status);
        // Approving a faculty registration assigns the staff id shown on the
        // faculty profile page (FAC-<zero-padded user id>), once.
        if (status == RegistrationStatus.APPROVED && user.getFacultyId() == null) {
            user.setFacultyId(String.format(java.util.Locale.ROOT, "FAC-%04d", user.getId()));
        }
        userRepository.save(user);
        log.info("Faculty account {} set to {} (by {})", user.getEmail(), status, requestedByUserId);
        return toUserResponse(user);
    }

    /**
     * Permanently removes an account and everything attached to it. Used both for
     * "delete my account" (self service) and for the admin Users panel.
     */
    @Transactional
    public void deleteAccount(Long userId, Long requestedByUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getRole() == Role.ADMIN && !userId.equals(requestedByUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin accounts cannot be deleted by another admin");
        }
        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("The last admin account cannot be deleted");
        }

        // Detach review credits left on other students' purchases before the user
        // row disappears, so those rows keep a valid (null) reviewer.
        purchaseRepository.clearReviewedBy(userId);
        purchaseRepository.deleteByUserId(userId);
        favoriteRepository.deleteByUserId(userId);
        feedbackRepository.deleteByUserId(userId);
        certificateRepository.deleteByUserId(userId);
        notificationRepository.deleteByTargetUserId(userId);


        userRepository.delete(user);
        log.info("Deleted account {} (requested by {})", user.getEmail(), requestedByUserId);
    }

    public static UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.isEmailVerified(), user.isEnabled(),
                user.isDeclarationAccepted(),
                user.registrationStatusOrDefault(),
                user.getCourse(), user.getBranch(), user.getYear(), user.getSemester(),
                user.getAddress(),
                user.getGender(), user.getEnrollmentNumber(),
                user.getDepartment(), user.getQualification(), user.getExperience(),
                user.getFacultyId(), user.getProfilePhotoUrl(),
                user.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
