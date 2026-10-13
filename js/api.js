// =====================================================
// TechCampus_Hub
// api.js
//
// Shared REST client for the Spring Boot backend.
//
// - Talks to the backend at API_BASE_URL (js/config.js).
// - Keeps the JWT in localStorage and sends it as
//   "Authorization: Bearer <token>".
// - Unwraps the backend envelope { success, message, data }
//   so callers get plain data; failures throw an Error whose
//   message is the backend's message.
// - Normalizers convert the backend's camelCase DTOs into
//   the snake_case row shapes the render/search code reads
//   (file_url, created_at, course_id, ...). Inverse
//   converters turn those row shapes back into camelCase
//   request bodies.
// =====================================================

import { API_BASE_URL } from "./config.js";

// =====================================================
// Token / user persistence
// =====================================================

const TOKEN_KEY = "campusnotes:authToken";
const USER_KEY = "campusnotes:authUser";

export function getAuthToken() {
    try {
        return window.localStorage.getItem(TOKEN_KEY);
    } catch (error) {
        return null;
    }
}

export function setAuthToken(token) {
    try {
        if (token) {
            window.localStorage.setItem(TOKEN_KEY, token);
        } else {
            window.localStorage.removeItem(TOKEN_KEY);
        }
    } catch (error) {
        console.warn("Auth token storage failed:", error);
    }
}

export function getStoredUser() {
    try {
        const raw = window.localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch (error) {
        return null;
    }
}

export function setStoredUser(user) {
    try {
        if (user) {
            window.localStorage.setItem(USER_KEY, JSON.stringify(user));
        } else {
            window.localStorage.removeItem(USER_KEY);
        }
    } catch (error) {
        console.warn("Auth user storage failed:", error);
    }
}

export function clearAuth() {
    setAuthToken(null);
    setStoredUser(null);
}

// =====================================================
// apiFetch — one call for every backend endpoint
// =====================================================
//
// options.body is JSON-serialized when present.
// Returns the envelope's `data` (or null).
// Throws Error(message) when the server says failure.
// =====================================================

export async function apiFetch(path, options = {}) {
    const {
        method = "GET",
        body,
        auth = true
    } = options;

    const headers = {};

    if (body !== undefined && body !== null) {
        headers["Content-Type"] = "application/json";
    }

    const token = getAuthToken();

    if (auth && token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    let response;

    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            method,
            headers,
            body: body !== undefined && body !== null
                ? JSON.stringify(body)
                : undefined
        });
    } catch (error) {
        throw new Error(
            "Could not reach the server. Please check your connection."
        );
    }

    let payload = null;

    try {
        payload = await response.json();
    } catch (error) {
        // 204 responses and proxy errors carry no JSON body.
    }

    if (!response.ok || payload?.success === false) {
        const message =
            payload?.message ||
            `Request failed (${response.status})`;

        const failure = new Error(message);

        failure.status = response.status;
        failure.fieldErrors = payload?.fieldErrors || null;

        throw failure;
    }

    return payload ? payload.data : null;
}

// =====================================================
// Small verb helpers
// =====================================================

export function apiGet(path, auth = true) {
    return apiFetch(path, { method: "GET", auth });
}

export function apiPost(path, body, auth = true) {
    return apiFetch(path, { method: "POST", body, auth });
}

export function apiPut(path, body, auth = true) {
    return apiFetch(path, { method: "PUT", body, auth });
}

export function apiDelete(path, auth = true) {
    return apiFetch(path, { method: "DELETE", auth });
}

// =====================================================
// Normalizers: backend DTO -> frontend snake_case row
// =====================================================

/** notes: fileUrl -> file_url, storagePath -> storage_path, ... */
export function normalizeNote(note) {
    if (!note) {
        return null;
    }

    return {
        id: note.id,
        title: note.title,
        subject: note.subject,
        course: note.course,
        branch: note.branch,
        year: note.year,
        semester: note.semester,
        description: note.description,
        file_url: note.fileUrl,
        storage_path: note.storagePath,
        course_id: note.courseId ?? null,
        course_title: note.courseTitle ?? null,
        created_at: note.createdAt
    };
}

/**
 * PYQs: the paper's year lives in examYear on the backend;
 * the frontend reads both `year` (admin edit form) and
 * `exam_year` (search/render), so expose both.
 */
export function normalizePyq(pyq) {
    if (!pyq) {
        return null;
    }

    return {
        id: pyq.id,
        title: pyq.title,
        subject: pyq.subject,
        course: pyq.course,
        branch: pyq.branch,
        semester: pyq.semester,
        year: pyq.examYear,
        exam_year: pyq.examYear,
        description: pyq.description,
        file_url: pyq.fileUrl,
        storage_path: pyq.storagePath,
        course_id: pyq.courseId ?? null,
        course_title: pyq.courseTitle ?? null,
        created_at: pyq.createdAt
    };
}

/** TNP: the backend calls the subject-area label `topic`. */
export function normalizeTnp(tnp) {
    if (!tnp) {
        return null;
    }

    return {
        id: tnp.id,
        title: tnp.title,
        subject: tnp.topic,
        topic: tnp.topic,
        description: tnp.description,
        file_url: tnp.fileUrl,
        storage_path: tnp.storagePath,
        course_id: tnp.courseId ?? null,
        course_title: tnp.courseTitle ?? null,
        created_at: tnp.createdAt
    };
}

/**
 * Timetables: the Cloudinary public id is exposed as BOTH
 * storage_path and cloudinary_public_id so the admin delete
 * and replace flows (which read storage_path) and the upload
 * flow (which wrote cloudinary_public_id) agree.
 */
export function normalizeTimetable(timetable) {
    if (!timetable) {
        return null;
    }

    const publicId = timetable.storagePath || null;

    return {
        id: timetable.id,
        title: timetable.title,
        course: timetable.course,
        semester: timetable.semester,
        exam_type: timetable.examType,
        exam_year: timetable.examYear,
        day_of_week: timetable.dayOfWeek,
        effective_from: timetable.effectiveFrom,
        file_url: timetable.fileUrl,
        storage_path: publicId,
        cloudinary_public_id: publicId,
        file_name: timetable.fileName,
        course_id: timetable.courseId ?? null,
        course_title: timetable.courseTitle ?? null,
        created_at: timetable.createdAt
    };
}

/** courses: price_inr / validity_days / sort_order / is_active. */
export function normalizeCourse(course) {
    if (!course) {
        return null;
    }

    return {
        id: course.id,
        title: course.title,
        code: course.code,
        category: course.category,
        description: course.description,
        price_inr: Number(course.priceInr) || 0,
        thumbnail_url: course.thumbnailUrl,
        validity_days: course.validityDays,
        slug: course.slug,
        highlights: Array.isArray(course.highlights)
            ? course.highlights
            : [],
        sort_order: course.sortOrder,
        is_active: course.active,
        created_at: course.createdAt
    };
}

/**
 * purchases: status is upper-case on the backend but the
 * frontend compares against "approved"/"pending"/"rejected",
 * and the expiry window is called expires_at client-side
 * (validUntil server-side).
 */
export function normalizePurchase(purchase) {
    if (!purchase) {
        return null;
    }

    return {
        id: purchase.id,
        course_id: purchase.courseId,
        course_title: purchase.courseTitle,
        user_id: purchase.userId,
        user_name: purchase.userName,
        user_email: purchase.userEmail,
        amount_inr: Number(purchase.amountInr) || 0,
        utr: purchase.utr,
        status: String(purchase.status || "").toLowerCase(),
        valid_from: purchase.validFrom,
        expires_at: purchase.validUntil,
        reviewed_at: purchase.reviewedAt,
        rejection_reason: purchase.rejectionReason,
        created_at: purchase.createdAt
    };
}

/** users/students: role is "ADMIN"/"STUDENT" -> "admin"/"student". */
export function normalizeUser(user) {
    if (!user) {
        return null;
    }

    return {
        id: user.id,
        name: user.name,
        email: user.email,
        phone: user.phone,
        course: user.course,
        branch: user.branch,
        year: user.year,
        semester: user.semester,
        role: String(user.role || "student").toLowerCase(),
        email_verified: user.emailVerified,
        created_at: user.createdAt
    };
}

const FAVORITE_TYPE_TO_CATEGORY = {
    COURSE: "course",
    NOTE: "notes",
    PYQ: "pyq",
    TNP_NOTE: "tnp",
    TIMETABLE: "timetable",
    CERTIFICATE: "certificate"
};

export const FAVORITE_CATEGORY_TO_TYPE = {
    course: "COURSE",
    notes: "NOTE",
    pyq: "PYQ",
    tnp: "TNP_NOTE",
    timetable: "TIMETABLE",
    certificate: "CERTIFICATE"
};

/** favorites: itemType (NOTE) -> category ("notes"), itemId -> item_id. */
export function normalizeFavorite(favorite) {
    if (!favorite) {
        return null;
    }

    return {
        id: favorite.id,
        category:
            FAVORITE_TYPE_TO_CATEGORY[favorite.itemType] || "notes",
        item_id: String(favorite.itemId ?? ""),
        item_title: favorite.itemTitle || "Untitled",
        created_at: favorite.createdAt
    };
}

/** feedback: camelCase mirrors of the same field names. */
export function normalizeFeedback(feedback) {
    if (!feedback) {
        return null;
    }

    return {
        id: feedback.id,
        user_id: feedback.userId,
        user_name: feedback.userName,
        user_email: feedback.userEmail,
        course_id: feedback.courseId,
        course_title: feedback.courseTitle,
        rating: feedback.rating,
        message: feedback.message,
        target_type: feedback.targetType || "website",
        target_label: feedback.targetLabel || "Website",
        created_at: feedback.createdAt
    };
}

/**
 * notifications: the frontend's `body` is the backend's
 * message; the frontend's `kind` (general/timetable/course/
 * notes/pyq/tnp) is the backend's `category`.
 */
export function normalizeNotification(notification) {
    if (!notification) {
        return null;
    }

    return {
        id: notification.id,
        title: notification.title,
        body: notification.message || "",
        message: notification.message || "",
        kind: notification.category || "general",
        category: notification.category || "general",
        audience: notification.audience || "all",
        created_by: notification.createdBy || "",
        user_id: notification.userId,
        broadcast: notification.broadcast,
        created_at: notification.createdAt
    };
}

/** certificate links: sort_order / is_active. */
export function normalizeCertificateLink(link) {
    if (!link) {
        return null;
    }

    return {
        id: link.id,
        title: link.title,
        url: link.url,
        description: link.description,
        sort_order: link.sortOrder,
        is_active: link.isActive,
        created_at: link.createdAt
    };
}

/** site settings: upi_id / gemini_url / telegram_link. */
export function normalizeSettings(settings) {
    if (!settings) {
        return null;
    }

    return {
        id: settings.id,
        upi_id: settings.upiId,
        gemini_url: settings.geminiUrl,
        telegram_link: settings.telegramLink,
        updated_at: settings.updatedAt
    };
}

// =====================================================
// Inverse converters: frontend row -> backend request
// =====================================================
//
// Keys whose value is undefined are dropped so partial
// updates (PUT) only touch the fields that changed.
// =====================================================

function compact(record) {
    const out = {};

    Object.entries(record).forEach(([key, value]) => {
        if (value !== undefined) {
            out[key] = value;
        }
    });

    return out;
}

export function noteToRequest(row = {}) {
    return compact({
        title: row.title ?? null,
        subject: row.subject,
        course: row.course,
        branch: row.branch,
        year: row.year,
        semester: row.semester,
        description: row.description,
        fileUrl: row.file_url,
        storagePath: row.storage_path,
        courseId: row.course_id
    });
}

export function pyqToRequest(row = {}) {
    return compact({
        title: row.title ?? null,
        subject: row.subject,
        course: row.course,
        branch: row.branch,
        semester: row.semester,
        examYear: row.year ?? row.exam_year,
        description: row.description,
        fileUrl: row.file_url,
        storagePath: row.storage_path,
        courseId: row.course_id
    });
}

export function tnpToRequest(row = {}) {
    return compact({
        title: row.title ?? null,
        topic: row.subject ?? row.topic,
        description: row.description,
        fileUrl: row.file_url,
        storagePath: row.storage_path,
        courseId: row.course_id
    });
}

export function timetableToRequest(row = {}) {
    const publicId =
        row.storage_path ?? row.cloudinary_public_id;

    return compact({
        title: row.title ?? null,
        course: row.course,
        semester: row.semester,
        examType: row.exam_type,
        examYear: row.exam_year,
        dayOfWeek: row.day_of_week,
        effectiveFrom: row.effective_from,
        fileUrl: row.file_url,
        storagePath: publicId,
        fileName: row.file_name,
        courseId: row.course_id
    });
}

export function courseToRequest(row = {}) {
    return compact({
        title: row.title,
        description: row.description,
        priceInr: row.price_inr,
        validityDays: row.validity_days,
        slug: row.slug,
        highlights: row.highlights,
        sortOrder: row.sort_order,
        active: row.is_active
    });
}

export function settingsToRequest(row = {}) {
    return compact({
        upiId: row.upi_id,
        geminiUrl: row.gemini_url,
        telegramLink: row.telegram_link
    });
}
