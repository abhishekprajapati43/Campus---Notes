// =====================================================
// TechCampus_Hub
// courses.js
//
// Course catalog, manual UPI purchases (admin approval),
// note-bundle gating and the shared commerce UI wiring.
//
// Storage: Spring Boot backend (/api/courses, /api/purchases,
// /api/settings). When the backend is unreachable the module
// falls back to the bundled catalog data (read-only) so the
// storefront still renders.
// =====================================================

import { $, escapeHtml, formatDate } from "./helpers.js";
import { currentUserRole, currentStudent } from "./state.js";

import {
    apiGet,
    apiPost,
    apiPut,
    apiDelete,
    getStoredUser,
    normalizeCourse,
    normalizePurchase,
    normalizeSettings,
    courseToRequest,
    settingsToRequest,
    approvePurchaseWithValidity,
    setPurchaseValidity,
    getCourseRating,
    getCourseReviews,
    submitCourseReview
} from "./api.js";

// =====================================================
// Constants
// =====================================================

export const DEFAULT_UPi_ID = "techcampushub@upi";

const UPi_STORAGE_KEY = "campusnotes:upiId";
const PURCHASES_STORAGE_KEY = "campusnotes:purchases";
const COURSES_LOCAL_KEY = "campusnotes:courses";

// Catalog used when the courses table does not exist yet.
const FALLBACK_COURSES = [
    {
        id: "local-mca-notes",
        slug: "mca-notes-bundle",
        title: "MCA Complete Notes Bundle",
        description: "Every semester's subject notes for MCA — organized by semester and subject, PDF downloads.",
        price_inr: 499,
        highlights: [
            "All MCA semesters",
            "Subject-wise PDF notes",
            "Lifetime access after approval",
            "New notes added regularly"
        ]
    },
    {
        id: "local-btech-notes",
        slug: "btech-cse-notes-bundle",
        title: "B.Tech CSE Notes Bundle",
        description: "Semester notes for Computer Science & Engineering — core subjects, units and topic notes.",
        price_inr: 599,
        highlights: [
            "All CSE semesters",
            "Unit-wise notes",
            "Lifetime access after approval",
            "New notes added regularly"
        ]
    },
    {
        id: "local-pyq-pack",
        slug: "pyq-mastery-pack",
        title: "Previous Year Papers (PYQ) Pack",
        description: "Previous year question papers with solutions — sorted by course, year and semester.",
        price_inr: 199,
        highlights: [
            "Years of question papers",
            "Course + semester sorted",
            "One-time payment",
            "Lifetime access"
        ]
    }
];

// =====================================================
// Module state
// =====================================================

let coursesCache = null;
let coursesMode = "cloud";        // "cloud" | "fallback"
let purchasesCache = [];          // current user's purchases
let ownershipLoaded = false;

// =====================================================
// Helpers
// =====================================================

export function formatPrice(value) {
    const amount = Number(value) || 0;

    if (amount <= 0) {
        return "Free";
    }

    return `₹${amount.toLocaleString("en-IN")}`;
}

function localRead(key, fallback) {
    try {
        const raw = window.localStorage.getItem(key);
        return raw ? JSON.parse(raw) : fallback;
    } catch (error) {
        console.warn("Local storage read failed:", error);
        return fallback;
    }
}

function localWrite(key, value) {
    try {
        window.localStorage.setItem(key, JSON.stringify(value));
        return true;
    } catch (error) {
        console.error("Local storage write failed:", error);
        return false;
    }
}

async function getIdentity() {
    const user = getStoredUser() || null;

    return {
        id: user?.id || null,
        email: user?.email || currentStudent?.email || "",
        name: user?.name || currentStudent?.name || ""
    };
}

export function storageMode() {
    return coursesMode;
}

// =====================================================
// Course catalog
// =====================================================

export async function loadCourses(force = false) {
    if (coursesCache && !force) {
        return coursesCache;
    }

    try {
        const rows = await apiGet("/api/courses", false);

        coursesMode = "cloud";
        coursesCache = Array.isArray(rows) && rows.length
            ? rows.map(normalizeCourse)
            : loadLocalCourses();
    } catch (error) {
        console.error("Courses loading error:", error);

        coursesMode = "fallback";
        coursesCache = loadLocalCourses();
    }

    return coursesCache;
}

// Local (offline) catalog: reads any browser-stored catalog
// first, then falls back to the bundled demo courses.
function loadLocalCourses() {
    const stored = localRead(COURSES_LOCAL_KEY, null);

    if (Array.isArray(stored) && stored.length) {
        return stored.map(course => ({ ...course }));
    }

    return FALLBACK_COURSES.map(course => ({ ...course }));
}

export function courseById(courseId) {
    if (!courseId) {
        return null;
    }

    const list = coursesCache || FALLBACK_COURSES;

    return list.find(course => String(course.id) === String(courseId)) || null;
}

export function courseTitleFor(courseId) {
    const course = courseById(courseId);

    return course ? course.title : "Paid course bundle";
}

// =====================================================
// Course management (admin)
// Launch, edit, delete, price and validity.
// =====================================================

function slugify(value) {
    return (
        String(value || "")
            .toLowerCase()
            .trim()
            .replace(/[^a-z0-9]+/g, "-")
            .replace(/^-+|-+$/g, "")
            .slice(0, 60) || "course"
    );
}

function uniqueSlug(base, existing) {
    const taken = new Set(
        existing.map(course => String(course.slug || ""))
    );

    let slug = base;
    let counter = 2;

    while (taken.has(slug)) {
        slug = `${base}-${counter}`;
        counter += 1;
    }

    return slug;
}

export async function createCourse(fields) {
    const title = String(fields.title || "").trim();

    if (!title) {
        throw new Error("Course title is required.");
    }

    const courses = await loadCourses(true);

    const row = {
        slug: uniqueSlug(
            slugify(fields.slug || title),
            courses
        ),
        title,
        description: String(fields.description || "").trim(),
        price_inr: Math.max(0, Number(fields.price_inr) || 0),
        validity_days: Math.max(1, Number(fields.validity_days) || 365),
        highlights: Array.isArray(fields.highlights)
            ? fields.highlights.filter(Boolean)
            : [],
        sort_order: Number(fields.sort_order) || courses.length + 1,
        is_active: fields.is_active !== false
    };

    const created = await apiPost(
        "/api/admin/courses",
        courseToRequest(row)
    );

    await loadCourses(true);

    await notifyCourseLaunch(title, row.description);

    return normalizeCourse(created);
}

// "New course launched" alert for every student.
async function notifyCourseLaunch(title, detail) {
    try {
        const { notifyContentUpload } = await import("./notifications.js");

        await notifyContentUpload("course", title, detail);
    } catch (alertError) {
        console.warn("Course notification failed:", alertError);
    }
}

export async function updateCourse(courseId, fields) {
    const patch = {};

    if (fields.title !== undefined) {
        const title = String(fields.title || "").trim();

        if (!title) {
            throw new Error("Course title is required.");
        }

        patch.title = title;
    }

    if (fields.description !== undefined) {
        patch.description = String(fields.description || "").trim();
    }

    if (fields.price_inr !== undefined) {
        patch.price_inr = Math.max(0, Number(fields.price_inr) || 0);
    }

    if (fields.validity_days !== undefined) {
        patch.validity_days = Math.max(1, Number(fields.validity_days) || 365);
    }

    if (fields.highlights !== undefined) {
        patch.highlights = Array.isArray(fields.highlights)
            ? fields.highlights.filter(Boolean)
            : [];
    }

    if (fields.is_active !== undefined) {
        patch.is_active = Boolean(fields.is_active);
    }

    // The backend PUT needs the required fields (title, price), so the
    // patch is merged onto the current course before sending.
    const existing = courseById(courseId);

    if (!existing) {
        throw new Error("Course not found.");
    }

    const updated = await apiPut(
        `/api/admin/courses/${courseId}`,
        courseToRequest({ ...existing, ...patch })
    );

    await loadCourses(true);

    return normalizeCourse(updated);
}

export async function deleteCourse(courseId) {
    await apiDelete(
        `/api/admin/courses/${courseId}`
    );

    await loadCourses(true);
}

// =====================================================
// Purchases — current user
// =====================================================

export async function refreshOwnership() {
    ownershipLoaded = true;
    purchasesCache = [];

    const identity = await getIdentity();

    if (!identity.id) {
        return purchasesCache;
    }

    try {
        const rows = await apiGet("/api/purchases/mine");

        purchasesCache = (Array.isArray(rows) ? rows : [])
            .map(normalizePurchase);
    } catch (error) {
        console.error("Purchases loading error:", error);

        const all = localRead(PURCHASES_STORAGE_KEY, []);
        purchasesCache = all.filter(
            row => String(row.user_id) === String(identity.id)
        );
    }

    return purchasesCache;
}

export function getPurchaseFor(courseId) {
    if (!courseId) {
        return null;
    }

    return purchasesCache.find(
        row => String(row.course_id) === String(courseId)
    ) || null;
}

export function purchaseStatusFor(courseId) {
    const purchase = getPurchaseFor(courseId);

    return purchase ? purchase.status : null;
}

export function getOwnedCourseIds() {
    return new Set(
        purchasesCache
            .filter(
                row => row.status === "approved" && !purchaseExpired(row)
            )
            .map(row => String(row.course_id))
    );
}

// A purchase stops unlocking content once the course
// validity (expires_at) has passed.
export function purchaseExpired(purchase) {
    if (!purchase || !purchase.expires_at) {
        return false;
    }

    return new Date(purchase.expires_at).getTime() <= Date.now();
}

// True while the student holds at least one approved,
// unexpired purchase (premium-only notifications).
export function hasActivePremium() {
    return purchasesCache.some(
        row => row.status === "approved" && !purchaseExpired(row)
    );
}

// A note is locked when it belongs to a paid bundle the
// student has not purchased or the purchase expired
// (admins always see everything).
export function isNoteLocked(note) {
    if (!note || !note.course_id) {
        return false;
    }

    if (currentUserRole === "admin") {
        return false;
    }

    const purchase = getPurchaseFor(note.course_id);

    return !(
        purchase &&
        purchase.status === "approved" &&
        !purchaseExpired(purchase)
    );
}

export async function submitPurchase({ course, utr }) {
    const identity = await getIdentity();

    if (!identity.id) {
        throw new Error("Please log in before purchasing a course.");
    }

    const trimmedUtr = String(utr || "").trim();

    if (trimmedUtr.length < 6) {
        throw new Error(
            "Please enter the transaction reference (UTR) with at least 6 characters."
        );
    }

    // The backend stores courses with numeric ids. The offline
    // fallback catalog uses string ids (e.g. "local-mca-notes")
    // which cannot be purchased. Catch that here for a clear
    // message instead of a confusing backend 400.
    const courseId = Number(course.id);

    if (!courseId) {
        throw new Error(
            "This course is not available for purchase right now. Please refresh the page and try again."
        );
    }

    const data = await apiPost(
        "/api/purchases",
        {
            courseId,
            utr: trimmedUtr
        }
    );

    const row = normalizePurchase(data);

    await refreshOwnership();

    return row;
}

// =====================================================
// Purchases — admin
// =====================================================

export async function loadAllPurchases() {
    try {
        const rows = await apiGet("/api/admin/purchases");

        return {
            rows: (Array.isArray(rows) ? rows : []).map(normalizePurchase),
            mode: "cloud"
        };
    } catch (error) {
        console.error("Admin purchases loading error:", error);

        return {
            rows: localRead(PURCHASES_STORAGE_KEY, []),
            mode: "local"
        };
    }
}

export async function setPurchaseStatus(purchaseId, status) {
    const normalized = status === "approved" ? "approved" : "rejected";

    // The backend computes the expiry window (course validity) on approval.
    const data = normalized === "approved"
        ? await apiPost(`/api/admin/purchases/${purchaseId}/approve`)
        : await apiPost(`/api/admin/purchases/${purchaseId}/reject`, {});

    return normalizePurchase(data);
}

// Admin: push the expiry further out (extend course expiry).
export async function extendPurchaseExpiry(purchaseId, days = 30) {
    const amount = Number(days) > 0 ? Number(days) : 30;

    const data = await apiPost(
        `/api/admin/purchases/${purchaseId}/extend-validity`,
        { days: amount }
    );

    return normalizePurchase(data);
}

// Admin: remove a purchase record entirely.
export async function deletePurchase(purchaseId) {
    await apiDelete(
        `/api/admin/purchases/${purchaseId}`
    );
}

// =====================================================
// UPI ID (site settings, localStorage fallback)
// =====================================================

export async function getUpiId() {
    try {
        const settings = normalizeSettings(
            await apiGet("/api/settings", false)
        );

        if (settings?.upi_id) {
            return settings.upi_id;
        }
    } catch (error) {
        console.error("Settings loading error:", error);
    }

    const localValue = localRead(UPi_STORAGE_KEY, null);

    return localValue || DEFAULT_UPi_ID;
}

export async function saveUpiId(value) {
    const trimmed = String(value || "").trim();

    if (!trimmed) {
        throw new Error("UPI ID cannot be empty.");
    }

    const settings = normalizeSettings(
        await apiPut(
            "/api/settings",
            settingsToRequest({ upi_id: trimmed })
        )
    );

    localWrite(UPi_STORAGE_KEY, trimmed);

    return { mode: "cloud", upiId: settings?.upi_id || trimmed };
}

export function buildUpiLink({ upiId, amount, title }) {
    const params = new URLSearchParams({
        pa: upiId,
        pn: "TechCampus_Hub",
        am: String(Number(amount) || 0),
        cu: "INR",
        tn: String(title || "Course purchase")
    });

    return `upi://pay?${params.toString()}`;
}

// =====================================================
// Admin upload form: assign notes to a paid bundle
// =====================================================

export async function populateBundleSelect(selectElement) {
    if (!selectElement || selectElement.dataset.loaded === "1") {
        return;
    }

    const courses = await loadCourses();

    const placeholder = selectElement.querySelector("option[value='']");

    if (placeholder) {
        placeholder.textContent = "Free — no bundle";
    }

    if (coursesMode !== "cloud") {
        selectElement.disabled = true;
        selectElement.dataset.loaded = "1";

        const option = document.createElement("option");
        option.textContent = "Available when the backend is offline";
        selectElement.append(option);

        return;
    }

    courses.forEach(course => {
        const option = document.createElement("option");
        option.value = course.id;
        option.textContent = `${course.title} (${formatPrice(course.price_inr)})`;
        selectElement.append(option);
    });

    selectElement.dataset.loaded = "1";
}

// =====================================================
// Page rendering — courses.html
// =====================================================

function statusBadge(courseId) {
    const status = purchaseStatusFor(courseId);

    if (status === "approved") {
        const purchase = getPurchaseFor(courseId);

        if (purchaseExpired(purchase)) {
            return `
                <span class="inline-flex items-center gap-2 rounded-lg bg-red-50
                         px-3 py-1.5 text-sm font-bold text-red-600">
                    <i class="fa-solid fa-hourglass-end"></i>
                    Expired
                </span>
            `;
        }

        return `
            <span class="inline-flex items-center gap-2 rounded-lg bg-emerald-50
                         px-3 py-1.5 text-sm font-bold text-emerald-700">
                <i class="fa-solid fa-circle-check"></i>
                Purchased
            </span>
        `;
    }

    if (status === "pending") {
        return `
            <span class="inline-flex items-center gap-2 rounded-lg bg-amber-50
                         px-3 py-1.5 text-sm font-bold text-amber-700">
                <i class="fa-regular fa-clock"></i>
                Awaiting approval
            </span>
        `;
    }

    return "";
}

async function renderCourseGrid() {
    const grid = $("courseGrid");

    if (!grid) {
        return;
    }

    const courses = await loadCourses();

    if (!courses.length) {
        grid.innerHTML = `
            <p class="text-slate-500">No courses available yet.</p>
        `;
        return;
    }

    grid.innerHTML = courses.map(course => {
        const status = purchaseStatusFor(course.id);
        const price = formatPrice(course.price_inr);
        const purchase = getPurchaseFor(course.id);
        const expired = purchase ? purchaseExpired(purchase) : false;

        let action = `
            <button
                type="button"
                class="buy-course-btn w-full rounded-xl bg-blue-600 hover:bg-blue-700
                       text-white font-bold py-3 transition"
                data-course-id="${escapeHtml(String(course.id))}"
            >
                Buy now — ${escapeHtml(price)}
            </button>
        `;

        if (expired) {
            action = `
                <button
                    type="button"
                    class="buy-course-btn w-full rounded-xl bg-blue-600 hover:bg-blue-700
                           text-white font-bold py-3 transition"
                    data-course-id="${escapeHtml(String(course.id))}"
                >
                    Renew — ${escapeHtml(price)}
                </button>
            `;
        } else if (status === "approved") {
            action = `
                <a href="./dashboard.html?category=notes"
                   class="block w-full text-center rounded-xl bg-emerald-600 hover:bg-emerald-700
                          text-white font-bold py-3 transition">
                    <i class="fa-solid fa-book-open mr-2"></i>Browse my notes
                </a>
            `;
        } else if (status === "pending") {
            action = `
                <button type="button" disabled
                    class="w-full rounded-xl bg-amber-100 text-amber-700
                           font-bold py-3 cursor-not-allowed">
                    <i class="fa-regular fa-clock mr-2"></i>Waiting for approval
                </button>
            `;
        }

        const highlights = (course.highlights || [])
            .map(item => `
                <li class="flex items-start gap-2 text-sm text-slate-600">
                    <i class="fa-solid fa-check text-emerald-500 mt-1"></i>
                    <span>${escapeHtml(item)}</span>
                </li>
            `)
            .join("");

        // Purchase expiry shown right on the student's course card.
        let expiryLine = "";

        if (status === "approved" && purchase?.expires_at) {
            expiryLine = `
                <p class="mt-3 text-xs font-semibold
                          ${expired ? "text-red-600" : "text-emerald-600"}">
                    <i class="fa-regular fa-clock mr-1"></i>
                    ${expired ? "Expired on" : "Valid until"}
                    ${escapeHtml(formatExpiryDateTime(purchase.expires_at))}
                </p>
            `;
        }

        const ratingHtml = ratingBadgeHtml(course);

        return `
            <article class="card p-6 flex flex-col">
                <div class="flex items-start justify-between gap-3">
                    <div class="w-12 h-12 rounded-xl bg-blue-50 text-blue-600
                                flex items-center justify-center text-xl shrink-0">
                        <i class="fa-solid fa-graduation-cap"></i>
                    </div>
                    ${statusBadge(course.id)}
                </div>
                <h3 class="mt-4 text-lg font-extrabold text-slate-900">
                    ${escapeHtml(course.title)}
                </h3>
                ${ratingHtml}
                <p class="mt-2 text-sm text-slate-500">
                    ${escapeHtml(course.description || "")}
                </p>
                <ul class="mt-4 space-y-2 flex-1">
                    ${highlights}
                </ul>
                <div class="mt-5 flex items-end justify-between gap-3">
                    <div>
                        <p class="text-xs uppercase tracking-wide text-slate-400 font-bold">
                            One-time
                        </p>
                        <p class="text-2xl font-extrabold text-slate-900">
                            ${escapeHtml(price)}
                        </p>
                    </div>
                    <button type="button"
                        class="reviews-trigger-btn text-xs font-bold text-slate-500
                               hover:text-blue-600 underline underline-offset-2"
                        data-course-id="${escapeHtml(String(course.id))}"
                        data-course-title="${escapeHtml(course.title)}">
                        Reviews
                    </button>
                </div>
                <div class="mt-4">
                    ${action}
                </div>
                ${expiryLine}
            </article>
        `;
    }).join("");
}

// =====================================================
// Rating badge shared by the course cards
// =====================================================

/** "⭐ 4.5 · 12 reviews" (or "No reviews yet") from course.rating_*. */
export function ratingBadgeHtml(course) {
    const count = Number(course?.rating_count) || 0;
    const average = Number(course?.rating_avg) || 0;

    if (!count) {
        return `
            <p class="mt-1 text-xs text-slate-400 font-semibold">
                <i class="fa-regular fa-star mr-1"></i>No reviews yet
            </p>
        `;
    }

    return `
        <p class="mt-1 text-xs text-slate-600 font-bold"
           title="${average.toFixed(1)} out of 5 from ${count} review${count === 1 ? "" : "s"}">
            <i class="fa-solid fa-star text-amber-400 mr-1"></i>${average.toFixed(1)}
            <span class="text-slate-400 font-semibold">
                · ${count} review${count === 1 ? "" : "s"}
            </span>
        </p>
    `;
}

// Full date + time for a purchase expiry (timestamptz).
export function formatExpiryDateTime(value) {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return String(value || "");
    }

    return `${date.toLocaleDateString("en-IN", {
        day: "2-digit",
        month: "short",
        year: "numeric"
    })}, ${date.toLocaleTimeString("en-IN", {
        hour: "2-digit",
        minute: "2-digit"
    })}`;
}

function validityText(row) {
    if (row.status !== "approved") {
        return "—";
    }

    if (!row.expires_at) {
        return "No expiry";
    }

    const expired = purchaseExpired(row);

    return formatExpiryDateTime(row.expires_at) +
        (expired ? " (expired)" : "");
}

async function renderPurchasesTable(containerId = "myPurchasesList") {
    const container = $(containerId);

    if (!container) {
        return;
    }

    await refreshOwnership();

    if (!purchasesCache.length) {
        container.innerHTML = `
            <p class="text-sm text-slate-500">
                You have not purchased any course yet.
            </p>
        `;
        return;
    }

    const statusLabel = {
        pending: `<span class="rounded-lg bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-700">Pending</span>`,
        approved: `<span class="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700">Approved</span>`,
        rejected: `<span class="rounded-lg bg-red-50 px-2.5 py-1 text-xs font-bold text-red-700">Rejected</span>`
    };

    container.innerHTML = `
        <div class="overflow-x-auto">
            <table class="w-full text-sm text-left">
                <thead>
                    <tr class="text-xs uppercase tracking-wide text-slate-400 border-b">
                        <th class="py-2 pr-4">Course</th>
                        <th class="py-2 pr-4">Amount</th>
                        <th class="py-2 pr-4">UTR</th>
                        <th class="py-2 pr-4">Date</th>
                        <th class="py-2 pr-4">Valid until</th>
                        <th class="py-2">Status</th>
                    </tr>
                </thead>
                <tbody>
                    ${purchasesCache.map(row => `
                        <tr class="border-b last:border-0">
                            <td class="py-3 pr-4 font-semibold text-slate-800">
                                ${escapeHtml(row.course_title || courseTitleFor(row.course_id))}
                            </td>
                            <td class="py-3 pr-4">${escapeHtml(formatPrice(row.amount_inr))}</td>
                            <td class="py-3 pr-4 font-mono text-xs">${escapeHtml(row.utr || "-")}</td>
                            <td class="py-3 pr-4">${escapeHtml(formatDate(row.created_at))}</td>
                            <td class="py-3 pr-4 ${purchaseExpired(row)
                                ? "text-red-600 font-semibold"
                                : ""}">
                                ${escapeHtml(validityText(row))}
                            </td>
                            <td class="py-3">${statusLabel[row.status] || escapeHtml(row.status || "-")}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        </div>
    `;
}

async function renderMyPurchases() {
    await renderPurchasesTable("myPurchasesList");
}

/**
 * Dashboard header: shows the nearest course expiry so students always
 * know how long their access lasts ("Course access valid until …").
 */
async function renderDashboardAccessLine() {
    const line = $("dashboardAccessLine");

    if (!line) {
        return;
    }

    await refreshOwnership();

    const active = purchasesCache
        .filter(row => row.status === "approved" && !purchaseExpired(row))
        .map(row => row.expires_at)
        .filter(Boolean)
        .sort();

    if (!active.length) {
        const expired = purchasesCache.some(
            row => row.status === "approved" && purchaseExpired(row)
        );

        line.textContent = expired
            ? "Course access expired — renew a course to keep learning."
            : "No active course access yet.";
        line.className = "text-xs font-bold mt-2 text-slate-400";
        return;
    }

    const nearest = active[0];

    line.innerHTML = `
        <i class="fa-regular fa-clock mr-1"></i>
        Course access valid until
        <span class="text-emerald-600">${escapeHtml(formatExpiryDateTime(nearest))}</span>
    `;
}

// payment-history.html reuses the same table.
export async function renderPaymentHistory() {
    await renderPurchasesTable("paymentHistoryList");
}

function coursesMessage(text, type = "info") {
    const box = $("coursesPageMessage");

    if (!box) {
        return;
    }

    box.textContent = text || "";
    box.className = "text-sm min-h-5 mb-4 px-4 py-3 rounded-xl";

    if (!text) {
        return;
    }

    if (type === "error") {
        box.classList.add("bg-red-50", "text-red-700");
    } else if (type === "success") {
        box.classList.add("bg-emerald-50", "text-emerald-700");
    } else {
        box.classList.add("bg-slate-100", "text-slate-600");
    }
}

// =====================================================
// Buy modal
// =====================================================

let activeCourse = null;
let upiId = DEFAULT_UPi_ID;

function openBuyModal(course) {
    const modal = $("buyModal");

    if (!modal) {
        return;
    }

    activeCourse = course;

    $("buyCourseTitle").textContent = course.title;
    $("buyCoursePrice").textContent = formatPrice(course.price_inr);
    $("buyUpiIdText").textContent = upiId;

    // Average rating of this course (details/checkout view).
    const ratingBox = $("buyCourseRating");
    if (ratingBox) {
        ratingBox.innerHTML = ratingBadgeHtml(course);
    }

    const upiLink = $("buyUpiLink");

    upiLink.href = buildUpiLink({
        upiId,
        amount: course.price_inr,
        title: course.title
    });

    $("buyUtrInput").value = "";
    coursesMessage("");

    modal.classList.remove("hidden");
    modal.classList.add("flex");
}

function closeBuyModal() {
    const modal = $("buyModal");

    if (!modal) {
        return;
    }

    modal.classList.add("hidden");
    modal.classList.remove("flex");
    activeCourse = null;
}

async function handleBuySubmit(event) {
    event.preventDefault();

    if (!activeCourse) {
        return;
    }

    const submitBtn = $("submitPurchaseBtn");
    const utr = $("buyUtrInput").value;

    submitBtn.disabled = true;
    submitBtn.textContent = "Submitting...";

    try {
        await submitPurchase({ course: activeCourse, utr });

        coursesMessage(
            "Request submitted! Your purchase will be unlocked after admin approval.",
            "success"
        );

        $("buyUtrInput").value = "";

        await renderCourseGrid();
        await renderMyPurchases();

        setTimeout(closeBuyModal, 1400);
    } catch (error) {
        console.error("Purchase error:", error);

        coursesMessage(
            error?.message || "Could not submit the purchase request.",
            "error"
        );
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML =
            `<i class="fa-solid fa-paper-plane mr-2"></i>Submit request`;
    }
}

function setupBuyModalEvents() {
    const modal = $("buyModal");

    if (!modal || modal.dataset.wired === "1") {
        return;
    }

    modal.dataset.wired = "1";

    $("closeBuyModalBtn")?.addEventListener("click", closeBuyModal);
    $("cancelBuyBtn")?.addEventListener("click", closeBuyModal);

    modal.addEventListener("click", event => {
        if (event.target === modal) {
            closeBuyModal();
        }
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape" && !modal.classList.contains("hidden")) {
            closeBuyModal();
        }
    });

    $("buyForm")?.addEventListener("submit", handleBuySubmit);
}

// =====================================================
// Admin — payment approvals panel
// =====================================================

function purchasesAdminMessage(text, type = "info") {
    const box = $("purchaseAdminMessage");

    if (!box) {
        return;
    }

    box.textContent = text || "";
    box.className = "text-sm min-h-5 mb-3 px-4 py-3 rounded-xl";

    if (!text) {
        return;
    }

    if (type === "error") {
        box.classList.add("bg-red-50", "text-red-700");
    } else {
        box.classList.add("bg-emerald-50", "text-emerald-700");
    }
}

async function renderAdminPurchases() {
    const list = $("purchaseRequestsList");
    const modeNote = $("purchaseModeNote");

    if (!list) {
        return;
    }

    const { rows, mode } = await loadAllPurchases();

    if (modeNote) {
        modeNote.textContent = mode === "local"
            ? "Storage: this browser only — the backend is unreachable."
            : "Storage: TechCampus Hub backend";
    }

    if (!rows.length) {
        list.innerHTML = `
            <p class="text-sm text-slate-500">No purchase requests yet.</p>
        `;
        return;
    }

    const statusBadgeHtml = {
        pending: `<span class="rounded-lg bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-700">Pending</span>`,
        approved: `<span class="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700">Approved</span>`,
        rejected: `<span class="rounded-lg bg-red-50 px-2.5 py-1 text-xs font-bold text-red-700">Rejected</span>`
    };

    list.innerHTML = rows.map(row => {
        let actions = "";

        if (row.status === "pending") {
            actions = `
                <div class="flex gap-2">
                    <button type="button"
                        class="approve-purchase-btn rounded-lg bg-emerald-600 hover:bg-emerald-700
                               text-white text-xs font-bold px-3 py-2"
                        title="Approve and choose the course validity"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        Approve
                    </button>
                    <button type="button"
                        class="reject-purchase-btn rounded-lg bg-red-50 hover:bg-red-100
                               text-red-600 text-xs font-bold px-3 py-2"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        Reject
                    </button>
                </div>
            `;
        } else if (row.status === "approved") {
            actions = `
                <div class="flex gap-2">
                    <button type="button"
                        class="validity-purchase-btn rounded-lg bg-indigo-50 hover:bg-indigo-100
                               text-indigo-700 text-xs font-bold px-3 py-2"
                        title="Set or modify the expiry date"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        <i class="fa-regular fa-calendar mr-1"></i>Validity
                    </button>
                    <button type="button"
                        class="extend-purchase-btn rounded-lg bg-blue-50 hover:bg-blue-100
                               text-blue-700 text-xs font-bold px-3 py-2"
                        title="Extend course expiry by 30 days"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        <i class="fa-solid fa-calendar-plus mr-1"></i>+30 days
                    </button>
                    <button type="button"
                        class="delete-purchase-btn rounded-lg bg-red-50 hover:bg-red-100
                               text-red-600 text-xs font-bold px-3 py-2"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        Remove
                    </button>
                </div>
            `;
        } else {
            actions = `
                <div class="flex gap-2">
                    <button type="button"
                        class="approve-purchase-btn rounded-lg bg-emerald-600 hover:bg-emerald-700
                               text-white text-xs font-bold px-3 py-2"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        Approve
                    </button>
                    <button type="button"
                        class="delete-purchase-btn rounded-lg bg-red-50 hover:bg-red-100
                               text-red-600 text-xs font-bold px-3 py-2"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        Remove
                    </button>
                </div>
            `;
        }

        return `
            <div class="border rounded-xl p-4 flex flex-wrap items-center gap-4 justify-between">
                <div class="min-w-0">
                    <p class="font-bold text-slate-800 truncate">
                        ${escapeHtml(row.course_title || "Course")} — ${escapeHtml(formatPrice(row.amount_inr))}
                    </p>
                    <p class="text-sm text-slate-500 truncate">
                        ${escapeHtml(row.user_name || "Student")} · ${escapeHtml(row.user_email || "-")}
                    </p>
                    <p class="text-xs text-slate-400 mt-1 font-mono">
                        UTR: ${escapeHtml(row.utr || "-")} · ${escapeHtml(formatDate(row.created_at))}
                    </p>
                    ${row.status === "approved"
                        ? `<p class="text-xs mt-1 font-semibold ${purchaseExpired(row)
                            ? "text-red-600"
                            : "text-emerald-600"}">
                               <i class="fa-regular fa-clock mr-1"></i>
                               Valid until: ${escapeHtml(validityText(row))}
                           </p>`
                        : ""}
                </div>
                <div class="flex items-center gap-3">
                    ${statusBadgeHtml[row.status] || ""}
                    ${actions}
                </div>
            </div>
        `;
    }).join("");
}

async function handlePurchaseAction(event) {
    const button = event.target.closest(
        ".approve-purchase-btn, .reject-purchase-btn, " +
        ".extend-purchase-btn, .delete-purchase-btn, " +
        ".validity-purchase-btn"
    );

    if (!button) {
        return;
    }

    const purchaseId = button.dataset.purchaseId;
    const approved = button.classList.contains("approve-purchase-btn");
    const extended = button.classList.contains("extend-purchase-btn");
    const removed = button.classList.contains("delete-purchase-btn");
    const validityEdit = button.classList.contains("validity-purchase-btn");

    // Approve / edit validity: ask the admin how long access should last.
    if (approved || validityEdit) {
        openValidityModal(purchaseId, approved ? "approve" : "edit");
        return;
    }

    button.disabled = true;

    try {
        if (extended) {
            await extendPurchaseExpiry(purchaseId, 30);

            purchasesAdminMessage(
                "Course expiry extended by 30 days.",
                "success"
            );
        } else if (removed) {
            await deletePurchase(purchaseId);

            purchasesAdminMessage("Purchase removed.", "success");
        } else {
            await setPurchaseStatus(
                purchaseId,
                approved ? "approved" : "rejected"
            );

            purchasesAdminMessage(
                approved
                    ? "Purchase approved — course unlocked for the student."
                    : "Purchase rejected.",
                approved ? "success" : "info"
            );
        }

        await renderAdminPurchases();
    } catch (error) {
        console.error("Purchase review error:", error);

        purchasesAdminMessage(
            error?.message || "Could not update the purchase.",
            "error"
        );

        button.disabled = false;
    }
}

// =====================================================
// Validity modal (admin): 30/90/180/365 days or a
// custom expiry date, used on approve and when editing
// the validity of an already approved purchase.
// =====================================================

let validityModalState = { purchaseId: null, mode: "approve" };

function openValidityModal(purchaseId, mode) {
    const modal = $("purchaseValidityModal");

    if (!modal) {
        return;
    }

    validityModalState = { purchaseId, mode };

    const title = $("validityModalTitle");
    const context = $("validityModalContext");
    const dateInput = $("validityDateInput");

    if (title) {
        title.textContent = mode === "approve"
            ? "Approve & set validity"
            : "Modify course validity";
    }

    if (context) {
        context.textContent = mode === "approve"
            ? "Choose how long the student keeps access to this course."
            : "Replace the current expiry with a new preset or exact date.";
    }

    if (dateInput) {
        dateInput.value = "";
    }

    document
        .querySelectorAll("#purchaseValidityModal .validity-preset-btn")
        .forEach(btn => {
            btn.classList.remove("bg-blue-600", "text-white", "border-blue-600");
        });

    modal.classList.remove("hidden");
    modal.classList.add("flex");
}

function closeValidityModal() {
    const modal = $("purchaseValidityModal");

    if (!modal) {
        return;
    }

    modal.classList.add("hidden");
    modal.classList.remove("flex");
    validityModalState = { purchaseId: null, mode: "approve" };
}

function selectedPresetDays() {
    const active = document.querySelector(
        "#purchaseValidityModal .validity-preset-btn.bg-blue-600"
    );

    return active ? Number(active.dataset.days) : 0;
}

async function handleValidityConfirm() {
    const { purchaseId, mode } = validityModalState;

    if (!purchaseId) {
        return;
    }

    const confirmButton = $("confirmValidityBtn");
    const dateInput = $("validityDateInput");
    const customDate = dateInput?.value
        ? `${dateInput.value}T23:59:59`
        : "";
    const presetDays = selectedPresetDays();

    if (!customDate && !presetDays && mode === "edit") {
        purchasesAdminMessage(
            "Pick a validity preset or a custom expiry date first.",
            "error"
        );
        return;
    }

    if (confirmButton) {
        confirmButton.disabled = true;
    }

    try {
        if (mode === "approve") {
            // Approve: exact date wins over preset; neither = course default.
            const validity = customDate
                ? { validUntil: customDate }
                : presetDays
                    ? { validityDays: presetDays }
                    : {};

            await approvePurchaseWithValidity(purchaseId, validity);

            purchasesAdminMessage(
                customDate
                    ? `Purchase approved — valid until ${customDate.slice(0, 10)}.`
                    : presetDays
                        ? `Purchase approved — ${presetDays} days of access.`
                        : "Purchase approved — course default validity applied.",
                "success"
            );
        } else {
            const validUntil = customDate || daysFromToday(presetDays);

            await setPurchaseValidity(purchaseId, validUntil);

            purchasesAdminMessage(
                `Expiry updated — valid until ${validUntil.slice(0, 10)}.`,
                "success"
            );
        }

        closeValidityModal();
        await renderAdminPurchases();
    } catch (error) {
        console.error("Validity update error:", error);

        purchasesAdminMessage(
            error?.message || "Could not update the validity.",
            "error"
        );
    } finally {
        if (confirmButton) {
            confirmButton.disabled = false;
        }
    }
}

function daysFromToday(days) {
    const date = new Date();
    date.setDate(date.getDate() + Number(days || 0));
    date.setHours(23, 59, 59, 0);

    const pad = value => String(value).padStart(2, "0");

    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function setupValidityModal() {
    const modal = $("purchaseValidityModal");

    if (!modal) {
        return;
    }

    // Preset buttons only select; Confirm applies the choice.
    modal.querySelectorAll(".validity-preset-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            modal
                .querySelectorAll(".validity-preset-btn")
                .forEach(other => {
                    other.classList.remove(
                        "bg-blue-600", "text-white", "border-blue-600"
                    );
                });

            btn.classList.add("bg-blue-600", "text-white", "border-blue-600");

            // An explicit preset clears a conflicting custom date.
            const dateInput = $("validityDateInput");
            if (dateInput) {
                dateInput.value = "";
            }
        });
    });

    // Choosing a date clears the preset selection.
    $("validityDateInput")?.addEventListener("change", () => {
        modal
            .querySelectorAll(".validity-preset-btn")
            .forEach(other => {
                other.classList.remove(
                    "bg-blue-600", "text-white", "border-blue-600"
                );
            });
    });

    $("confirmValidityBtn")?.addEventListener("click", handleValidityConfirm);
    $("cancelValidityBtn")?.addEventListener("click", closeValidityModal);
    $("closeValidityModalBtn")?.addEventListener("click", closeValidityModal);

    modal.addEventListener("click", event => {
        if (event.target === modal) {
            closeValidityModal();
        }
    });
}

// =====================================================
// Reviews modal: average + review list, and the rating
// form (verified purchasers only — enforced server-side).
// =====================================================

let reviewsModalState = {
    courseId: null,
    rating: 0,
    canReview: false
};

function closeReviewsModal() {
    const modal = $("courseReviewsModal");

    if (!modal) {
        return;
    }

    modal.classList.add("hidden");
    modal.classList.remove("flex");
    reviewsModalState = { courseId: null, rating: 0, canReview: false };
}

function setStarPicker(rating) {
    reviewsModalState.rating = Number(rating) || 0;

    document.querySelectorAll("#reviewStarPicker .review-star-btn")
        .forEach(btn => {
            const active = Number(btn.dataset.rating) <= reviewsModalState.rating;
            btn.classList.toggle("text-amber-400", active);
            btn.classList.toggle("text-slate-300", !active);
        });

    const label = $("reviewRatingLabel");

    if (label) {
        const names = ["", "Poor", "Fair", "Good", "Very good", "Excellent"];
        label.textContent = reviewsModalState.rating
            ? `${reviewsModalState.rating}/5 — ${names[reviewsModalState.rating]}`
            : "Select 1-5 stars";
    }
}

function starRow(rating) {
    let html = "";

    for (let i = 1; i <= 5; i++) {
        html += `<i class="fa-${i <= rating ? "solid" : "regular"} fa-star
                    ${i <= rating ? "text-amber-400" : "text-slate-300"}"></i>`;
    }

    return html;
}

function summaryHtml(summary) {
    const count = Number(summary?.count) || 0;
    const average = Number(summary?.average) || 0;

    if (!count) {
        return `
            <div class="rounded-xl bg-slate-50 p-4 text-sm text-slate-500 font-semibold">
                No reviews yet — your rating will be the first.
            </div>
        `;
    }

    return `
        <div class="rounded-xl bg-slate-50 p-4 flex items-center gap-4">
            <div class="text-center">
                <p class="text-3xl font-extrabold text-slate-900">${average.toFixed(1)}</p>
                <p class="text-xs text-amber-400">${starRow(Math.round(average))}</p>
            </div>
            <div class="text-sm text-slate-600 font-semibold">
                ${count} review${count === 1 ? "" : "s"} from verified purchasers
            </div>
        </div>
    `;
}

function reviewItemHtml(review) {
    const rating = Number(review.rating) || 0;
    const name = review.userName || "Student";
    const when = review.createdAt
        ? formatDate(review.createdAt)
        : "";

    return `
        <div class="border border-slate-200 rounded-xl p-3">
            <div class="flex items-center justify-between gap-2">
                <p class="font-bold text-slate-800 text-sm truncate">
                    ${escapeHtml(name)}
                </p>
                <span class="text-amber-400 text-xs shrink-0">${starRow(rating)}</span>
            </div>
            ${review.comment
                ? `<p class="text-sm text-slate-600 mt-1">${escapeHtml(review.comment)}</p>`
                : ""}
            ${when
                ? `<p class="text-xs text-slate-400 mt-1">${escapeHtml(when)}</p>`
                : ""}
        </div>
    `;
}

async function openReviewsModal(courseId, courseTitle) {
    const modal = $("courseReviewsModal");

    if (!modal) {
        return;
    }

    reviewsModalState = { courseId, rating: 0, canReview: false };

    const title = $("reviewsModalTitle");
    const summaryBox = $("reviewsModalSummary");
    const list = $("reviewList");
    const note = $("reviewListNote");

    if (title) {
        title.textContent = courseTitle || "Course";
    }

    if (summaryBox) {
        summaryBox.innerHTML = `<p class="text-sm text-slate-500">Loading…</p>`;
    }

    if (list) {
        list.innerHTML = `<p class="text-sm text-slate-500">Loading reviews…</p>`;
    }

    modal.classList.remove("hidden");
    modal.classList.add("flex");

    // Make sure the purchase cache is fresh before deciding whether the
    // visitor may rate this course.
    await refreshOwnership();

    try {
        const [summary, reviews] = await Promise.all([
            getCourseRating(courseId),
            getCourseReviews(courseId)
        ]);

        if (summaryBox) {
            summaryBox.innerHTML = summaryHtml(summary);
        }

        const rows = Array.isArray(reviews) ? reviews : [];

        if (list) {
            list.innerHTML = rows.length
                ? rows.map(reviewItemHtml).join("")
                : `<p class="text-sm text-slate-500">No reviews yet.</p>`;
        }

        // The signed-in student may rate only their own approved course.
        const status = purchaseStatusFor(courseId);
        const owned = status === "approved";
        const myReview = rows.find(
            review => String(review.userId) === String(getStoredUser()?.id)
        );

        const formBox = $("reviewFormBox");

        if (formBox) {
            formBox.classList.toggle("hidden", !owned);

            if (owned) {
                reviewsModalState.canReview = true;

                const heading = $("reviewFormHeading");
                const comment = $("reviewCommentInput");

                if (heading) {
                    heading.textContent = myReview
                        ? "Update your review"
                        : "Rate this course";
                }

                if (comment) {
                    comment.value = myReview?.comment || "";
                }

                setStarPicker(myReview?.rating || 0);
            }
        }

        if (note) {
            note.textContent = owned
                ? "Reviews from students who purchased this course."
                : "Buy this course to leave a rating and review.";
        }
    } catch (error) {
        console.error("Reviews loading error:", error);

        if (summaryBox) {
            summaryBox.innerHTML = "";
        }

        if (list) {
            list.innerHTML = `
                <p class="text-sm text-red-600">
                    ${escapeHtml(error.message || "Could not load reviews.")}
                </p>
            `;
        }
    }
}

async function handleReviewSubmit() {
    const { courseId, rating, canReview } = reviewsModalState;
    const message = $("reviewFormMessage");
    const button = $("submitReviewBtn");

    const setNote = (text, isError = false) => {
        if (message) {
            message.textContent = text || "";
            message.className = `text-xs flex-1 ${
                isError ? "text-red-600 font-semibold" : "text-slate-500"
            }`;
        }
    };

    if (!courseId || !canReview) {
        return;
    }

    if (!rating) {
        setNote("Please select a star rating first.", true);
        return;
    }

    if (button) {
        button.disabled = true;
    }

    try {
        const comment = $("reviewCommentInput")?.value || "";

        await submitCourseReview(courseId, rating, comment);

        setNote("Thanks! Your review has been saved.");

        // Refresh the average + list in place.
        const [summary, reviews] = await Promise.all([
            getCourseRating(courseId),
            getCourseReviews(courseId)
        ]);

        const summaryBox = $("reviewsModalSummary");
        const list = $("reviewList");

        if (summaryBox) {
            summaryBox.innerHTML = summaryHtml(summary);
        }

        if (list) {
            const rows = Array.isArray(reviews) ? reviews : [];
            list.innerHTML = rows.length
                ? rows.map(reviewItemHtml).join("")
                : `<p class="text-sm text-slate-500">No reviews yet.</p>`;
        }

        const heading = $("reviewFormHeading");
        if (heading) {
            heading.textContent = "Update your review";
        }

        // Cards show the live average — bust the catalog cache first so the
        // refreshed ratingCount/averageRating reach the grid.
        loadCourses(true)
            .then(() => renderCourseGrid())
            .catch(() => {});
    } catch (error) {
        console.error("Review submit error:", error);
        setNote(error.message || "Could not save your review.", true);
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

function setupReviewsModal() {
    const modal = $("courseReviewsModal");

    if (!modal) {
        return;
    }

    $("closeReviewsModalBtn")?.addEventListener("click", closeReviewsModal);
    $("submitReviewBtn")?.addEventListener("click", handleReviewSubmit);

    modal.addEventListener("click", event => {
        if (event.target === modal) {
            closeReviewsModal();
        }
    });

    document.querySelectorAll("#reviewStarPicker .review-star-btn")
        .forEach(btn => {
            btn.addEventListener("click", () => {
                setStarPicker(btn.dataset.rating);
            });
        });
}

async function handleUpiSave() {
    const input = $("upiIdInput");

    if (!input) {
        return;
    }

    try {
        const result = await saveUpiId(input.value);

        purchasesAdminMessage(
            result.mode === "local"
                ? "UPI ID saved (also cached locally — the backend is unreachable)."
                : "UPI ID saved.",
            "success"
        );
    } catch (error) {
        console.error("UPI ID save error:", error);

        purchasesAdminMessage(
            error?.message || "Could not save the UPI ID.",
            "error"
        );
    }
}

// =====================================================
// Admin — course management (admin.html)
// Launch, edit, delete, price and validity.
// =====================================================

function courseAdminMessage(text, type = "info") {
    const box = $("courseAdminMessage");

    if (!box) {
        return;
    }

    box.textContent = text || "";
    box.className = "text-sm min-h-5 mb-3 px-4 py-3 rounded-xl";

    if (!text) {
        return;
    }

    if (type === "error") {
        box.classList.add("bg-red-50", "text-red-700");
    } else {
        box.classList.add("bg-emerald-50", "text-emerald-700");
    }
}

async function renderAdminCourseList() {
    const list = $("adminCoursesList");

    if (!list) {
        return;
    }

    const courses = await loadCourses();

    if (!courses.length) {
        list.innerHTML = `
            <p class="text-sm text-slate-500">No courses yet — launch your first course.</p>
        `;
        return;
    }

    list.innerHTML = courses.map(course => {
        const validity = Number(course.validity_days) > 0
            ? `${Number(course.validity_days)} days`
            : "365 days";

        return `
            <div class="border rounded-xl p-4 flex flex-wrap items-center gap-4 justify-between">
                <div class="min-w-0">
                    <p class="font-bold text-slate-800 truncate">
                        ${escapeHtml(course.title || "Untitled course")}
                    </p>
                    <p class="text-sm text-slate-500 truncate">
                        ${escapeHtml(course.description || "")}
                    </p>
                    <p class="text-xs text-slate-400 mt-1">
                        ${escapeHtml(formatPrice(course.price_inr))} ·
                        Validity ${escapeHtml(validity)} ·
                        ${course.is_active === false ? "Hidden" : "Active"}
                    </p>
                </div>
                <div class="flex gap-2">
                    <button type="button"
                        class="edit-course-btn rounded-lg bg-blue-50 hover:bg-blue-100
                               text-blue-700 text-xs font-bold px-3 py-2"
                        data-course-id="${escapeHtml(String(course.id))}">
                        Edit
                    </button>
                    <button type="button"
                        class="delete-course-btn rounded-lg bg-red-50 hover:bg-red-100
                               text-red-600 text-xs font-bold px-3 py-2"
                        data-course-id="${escapeHtml(String(course.id))}">
                        Delete
                    </button>
                </div>
            </div>
        `;
    }).join("");
}

function setCourseFieldValue(id, value) {
    const element = $(id);

    if (element) {
        element.value =
            value === null || value === undefined ? "" : String(value);
    }
}

function resetCourseForm() {
    const form = $("courseAdminForm");

    if (!form) {
        return;
    }

    delete form.dataset.editingId;
    form.reset();

    const saveBtn = $("saveCourseBtn");

    if (saveBtn) {
        saveBtn.innerHTML =
            `<i class="fa-solid fa-rocket mr-2"></i>Launch course`;
    }

    $("cancelCourseEditBtn")?.classList.add("hidden");
}

async function handleCourseFormSubmit(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const editingId = form.dataset.editingId || null;

    const fields = {
        title: $("courseTitleInput")?.value,
        description: $("courseDescriptionInput")?.value,
        price_inr: $("coursePriceInput")?.value,
        validity_days: $("courseValidityInput")?.value,
        highlights: String($("courseHighlightsInput")?.value || "")
            .split(",")
            .map(part => part.trim())
            .filter(Boolean)
    };

    try {
        if (editingId) {
            await updateCourse(editingId, fields);
            courseAdminMessage("Course updated.", "success");
        } else {
            await createCourse(fields);
            courseAdminMessage(
                "Course launched — students have been notified.",
                "success"
            );
        }

        resetCourseForm();

        await renderAdminCourseList();
        await renderCourseGrid();
    } catch (error) {
        console.error("Course save error:", error);

        courseAdminMessage(
            error?.message || "Could not save the course.",
            "error"
        );
    }
}

async function handleCourseListClick(event) {
    const editButton = event.target.closest(".edit-course-btn");
    const deleteButton = event.target.closest(".delete-course-btn");

    if (!editButton && !deleteButton) {
        return;
    }

    const courses = await loadCourses();

    const course = courses.find(
        item => String(item.id) === String(
            (editButton || deleteButton).dataset.courseId
        )
    );

    if (!course) {
        courseAdminMessage("Course not found.", "error");
        return;
    }

    if (editButton) {
        const form = $("courseAdminForm");

        if (form) {
            form.dataset.editingId = String(course.id);
        }

        setCourseFieldValue("courseTitleInput", course.title);
        setCourseFieldValue(
            "courseDescriptionInput",
            course.description
        );
        setCourseFieldValue("coursePriceInput", course.price_inr);
        setCourseFieldValue(
            "courseValidityInput",
            course.validity_days ?? 365
        );
        setCourseFieldValue(
            "courseHighlightsInput",
            (course.highlights || []).join(", ")
        );

        const saveBtn = $("saveCourseBtn");

        if (saveBtn) {
            saveBtn.innerHTML =
                `<i class="fa-solid fa-check mr-2"></i>Save changes`;
        }

        $("cancelCourseEditBtn")?.classList.remove("hidden");

        courseAdminMessage(`Editing "${course.title}".`, "info");

        form?.scrollIntoView({ behavior: "smooth", block: "center" });

        return;
    }

    const confirmed = window.confirm(
        `Delete course "${course.title}"? Its notes will become free.`
    );

    if (!confirmed) {
        return;
    }

    try {
        await deleteCourse(course.id);

        courseAdminMessage("Course deleted.", "success");

        await renderAdminCourseList();
        await renderCourseGrid();
    } catch (error) {
        console.error("Course delete error:", error);

        courseAdminMessage(
            error?.message || "Could not delete the course.",
            "error"
        );
    }
}

export function setupCourseAdmin() {
    const form = $("courseAdminForm");

    if (!form) {
        return;
    }

    form.addEventListener("submit", handleCourseFormSubmit);

    $("cancelCourseEditBtn")?.addEventListener("click", () => {
        resetCourseForm();
        courseAdminMessage("");
    });

    $("adminCoursesList")?.addEventListener(
        "click",
        handleCourseListClick
    );

    $("refreshCoursesBtn")?.addEventListener("click", async () => {
        try {
            await loadCourses(true);
            await renderAdminCourseList();
        } catch (error) {
            console.error("Courses refresh error:", error);
        }
    });

    renderAdminCourseList().catch(error => {
        console.error("Admin course list render error:", error);
    });
}

// =====================================================
// Wiring (called from events.js setupEvents)
// =====================================================

export function setupCommerceEvents() {
    // ---------------------------------------------
    // dashboard.html — course access / expiry line
    // ---------------------------------------------
    renderDashboardAccessLine().catch(error => {
        console.error("Dashboard access line error:", error);
    });

    // ---------------------------------------------
    // courses.html
    // ---------------------------------------------
    if ($("courseGrid")) {
        upiId = DEFAULT_UPi_ID;

        getUpiId().then(value => {
            upiId = value;
        }).catch(error => {
            console.error("UPI ID load error:", error);
        });

        renderCourseGrid().catch(error => {
            console.error("Course grid render error:", error);
        });

        renderMyPurchases().catch(error => {
            console.error("Purchases render error:", error);
        });

        $("courseGrid").addEventListener("click", event => {
            const reviewsBtn = event.target.closest(".reviews-trigger-btn");

            if (reviewsBtn) {
                openReviewsModal(
                    reviewsBtn.dataset.courseId,
                    reviewsBtn.dataset.courseTitle
                );
                return;
            }

            const button = event.target.closest(".buy-course-btn");

            if (!button) {
                return;
            }

            loadCourses().then(courses => {
                const course = courses.find(
                    item => String(item.id) === button.dataset.courseId
                );

                if (course) {
                    openBuyModal(course);
                }
            });
        });

        setupBuyModalEvents();
        setupReviewsModal();
    }

    // ---------------------------------------------
    // admin.html — payment approvals
    // ---------------------------------------------
    if ($("purchaseRequestsList")) {
        renderAdminPurchases().catch(error => {
            console.error("Admin purchases render error:", error);
        });

        setupValidityModal();

        getUpiId().then(value => {
            const input = $("upiIdInput");

            if (input && !input.value) {
                input.value = value;
            }
        }).catch(error => {
            console.error("UPI ID load error:", error);
        });

        $("purchaseRequestsList").addEventListener(
            "click",
            handlePurchaseAction
        );

        $("refreshPurchasesBtn")?.addEventListener("click", () => {
            renderAdminPurchases().catch(error => {
                console.error("Admin purchases render error:", error);
            });
        });

        $("updateUpiIdBtn")?.addEventListener("click", handleUpiSave);
    }

    // ---------------------------------------------
    // payment-history.html
    // ---------------------------------------------
    if ($("paymentHistoryList")) {
        renderPaymentHistory().catch(error => {
            console.error("Payment history render error:", error);
        });
    }

    // ---------------------------------------------
    // admin.html — course management
    // ---------------------------------------------
    setupCourseAdmin();

    // ---------------------------------------------
    // admin upload form: bundle selectors
    //         (notes + paid TNP notes)
    // ---------------------------------------------
    ["noteCourseBundle", "tnpCourseBundle"].forEach(id => {
        const select = $(id);

        if (select) {
            populateBundleSelect(select).catch(error => {
                console.error("Bundle select populate error:", error);
            });
        }
    });
}
