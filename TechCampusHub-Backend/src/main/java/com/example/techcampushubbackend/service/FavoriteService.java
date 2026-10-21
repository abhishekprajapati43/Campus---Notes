package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.EngagementDtos.FavoriteRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.FavoriteResponse;
import com.example.techcampushubbackend.entity.Favorite;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CertificateRepository;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.FavoriteRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-user favorites. Content items (note / PYQ / TNP note / timetable) are
 * resolved through their owning service's {@code getVisible} check, so a
 * favorite can only ever be added to - or shown for - an item the caller
 * could actually open: this keeps favorites consistent with the program
 * (course) scoping of the dashboard (an MCA student cannot favorite, or see,
 * a B.Tech paper) and with bundle/purchase gating. Courses and certificates
 * are not program-scoped, so they only require an existence check.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final CourseRepository courseRepository;
    private final CertificateRepository certificateRepository;
    private final NoteService noteService;
    private final PyqService pyqService;
    private final TnpNoteService tnpNoteService;
    private final TimetableService timetableService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<FavoriteResponse> listMine(String itemType) {
        User user = currentUserService.requireUser();
        List<Favorite> favorites = (itemType == null || itemType.isBlank())
                ? favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                : favoriteRepository.findByUserIdAndItemTypeOrderByCreatedAtDesc(
                        user.getId(), parseType(itemType));
        List<FavoriteResponse> visible = new ArrayList<>();
        for (Favorite favorite : favorites) {
            String title = resolveVisibleTitle(favorite.getItemType(), favorite.getItemId());
            // Stale rows (item deleted, re-assigned to another program, or an
            // expired bundle) resolve to null - they stay in the table but
            // never surface on the student's favorites page.
            if (title != null) {
                visible.add(toResponse(favorite, title));
            }
        }
        return visible;
    }

    @Transactional
    public FavoriteResponse add(FavoriteRequest request) {
        User user = currentUserService.requireUser();
        Favorite.ItemType type = parseType(request.itemType());
        String title = resolveVisibleTitle(type, request.itemId());
        if (title == null) {
            // Missing, another program's item, or locked content - all
            // reported the same way so ids cannot be probed.
            throw new ResourceNotFoundException(type + " item", request.itemId());
        }

        var existing = favoriteRepository
                .findByUserIdAndItemTypeAndItemId(user.getId(), type, request.itemId());
        if (existing.isPresent()) {
            return toResponse(existing.get(), title); // idempotent
        }
        Favorite favorite = Favorite.builder()
                .user(user)
                .itemType(type)
                .itemId(request.itemId())
                .build();
        return toResponse(favoriteRepository.save(favorite), title);
    }

    @Transactional
    public void remove(String itemType, Long itemId) {
        User user = currentUserService.requireUser();
        Favorite.ItemType type = parseType(itemType);
        favoriteRepository.deleteByUserIdAndItemTypeAndItemId(user.getId(), type, itemId);
    }

    /**
     * Resolves the item's display title if - and only if - the item exists
     * and is visible to the current caller; returns null otherwise. Content
     * types delegate to the owning service so favorites inherit exactly the
     * same program scoping and bundle gating as the dashboard listings.
     */
    private String resolveVisibleTitle(Favorite.ItemType type, Long itemId) {
        try {
            return switch (type) {
                case COURSE -> courseRepository.findById(itemId).map(c -> c.getTitle()).orElse(null);
                case NOTE -> noteService.getVisible(itemId).title();
                case PYQ -> pyqService.getVisible(itemId).title();
                case TNP_NOTE -> tnpNoteService.getVisible(itemId).title();
                case TIMETABLE -> timetableService.getVisible(itemId).title();
                case CERTIFICATE -> certificateRepository.findById(itemId).map(c -> c.getTitle()).orElse(null);
            };
        } catch (ResourceNotFoundException ex) {
            return null; // not visible to this user (wrong program / locked / deleted)
        }
    }

    private FavoriteResponse toResponse(Favorite favorite, String itemTitle) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getItemType().name(),
                favorite.getItemId(),
                itemTitle,
                favorite.getCreatedAt());
    }

    private static Favorite.ItemType parseType(String raw) {
        try {
            return Favorite.ItemType.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid itemType: " + raw
                    + " (expected COURSE, NOTE, PYQ, TNP_NOTE, TIMETABLE or CERTIFICATE)");
        }
    }
}
