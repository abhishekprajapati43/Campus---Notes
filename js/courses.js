// =====================================================
// TechCampus_Hub
// courses.js
//
// Course catalog, manual UPI purchases (admin approval),
// note-bundle gating and the shared commerce UI wiring.
//
// Storage: uses the Supabase tables created by
// sql/setup.sql. When those tables are missing the
// module falls back to bundled catalog data and
// this-browser-only purchase storage so the flow still
// works before the migration is run.
// =====================================================

import { supabase } from "../supabase.js";
import { $, escapeHtml, formatDate } from "./helpers.js";
import { currentUserRole, currentStudent } from "./state.js";

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

function isMissingTable(error) {
    if (!error) {
        return false;
    }

    return (
        error.code === "PGRST205" ||
        error.code === "42P01" ||
        /could not find the table|does not exist|schema cache/i.test(
            String(error.message || "")
        )
    );
}

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
    const { data } = await supabase.auth.getSession();

    const user = data?.session?.user || null;

    return {
        id: user?.id || null,
        email: user?.email || currentStudent?.email || "",
        name: currentStudent?.name || user?.user_metadata?.name || ""
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

    const {
        data,
        error
    } = await supabase
        .from("courses")
        .select("*")
        .order("sort_order", { ascending: true });

    if (error) {
        if (!isMissingTable(error)) {
            console.error("Courses loading error:", error);
        }

        coursesMode = "fallback";
        coursesCache = loadLocalCourses();

        return coursesCache;
    }

    coursesMode = "cloud";
    coursesCache = data?.length
        ? data
        : loadLocalCourses();

    return coursesCache;
}

// Local (pre-migration) catalog: admin edits made before
// the courses table exists are stored in this browser.
function loadLocalCourses() {
    const stored = localRead(COURSES_LOCAL_KEY, null);

    if (Array.isArray(stored) && stored.length) {
        return stored.map(course => ({ ...course }));
    }

    return FALLBACK_COURSES.map(course => ({ ...course }));
}

function saveLocalCourses(list) {
    localWrite(COURSES_LOCAL_KEY, list.map(course => ({ ...course })));

    coursesMode = "fallback";
    coursesCache = list.map(course => ({ ...course }));
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

    const {
        data,
        error
    } = await supabase
        .from("courses")
        .insert(row)
        .select()
        .single();

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const localRow = {
            ...row,
            id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
        };

        const list = loadLocalCourses();
        list.push(localRow);
        saveLocalCourses(list);

        await notifyCourseLaunch(title, row.description);

        return localRow;
    }

    await loadCourses(true);

    await notifyCourseLaunch(title, row.description);

    return data;
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

    const {
        error
    } = await supabase
        .from("courses")
        .update(patch)
        .eq("id", courseId);

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const list = loadLocalCourses();
        const index = list.findIndex(
            course => String(course.id) === String(courseId)
        );

        if (index === -1) {
            throw new Error("Course not found.");
        }

        list[index] = { ...list[index], ...patch };
        saveLocalCourses(list);

        return list[index];
    }

    await loadCourses(true);

    return patch;
}

export async function deleteCourse(courseId) {
    const {
        error
    } = await supabase
        .from("courses")
        .delete()
        .eq("id", courseId);

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const list = loadLocalCourses();

        saveLocalCourses(
            list.filter(
                course => String(course.id) !== String(courseId)
            )
        );

        return;
    }

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

    const {
        data,
        error
    } = await supabase
        .from("course_purchases")
        .select("*")
        .eq("user_id", identity.id)
        .order("created_at", { ascending: false });

    if (error) {
        if (!isMissingTable(error)) {
            console.error("Purchases loading error:", error);
        }

        const all = localRead(PURCHASES_STORAGE_KEY, []);
        purchasesCache = all.filter(
            row => String(row.user_id) === String(identity.id)
        );

        return purchasesCache;
    }

    purchasesCache = data || [];

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

    const row = {
        user_id: identity.id,
        user_email: identity.email,
        user_name: identity.name,
        course_id: course.id,
        course_title: course.title,
        amount_inr: Number(course.price_inr) || 0,
        utr: trimmedUtr,
        status: "pending",
        created_at: new Date().toISOString()
    };

    const {
        data,
        error
    } = await supabase
        .from("course_purchases")
        .insert(row)
        .select()
        .single();

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        // Demo storage: courses/purchases tables not created yet.
        const all = localRead(PURCHASES_STORAGE_KEY, []);
        const localRow = {
            ...row,
            id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
        };

        all.unshift(localRow);
        localWrite(PURCHASES_STORAGE_KEY, all);

        await refreshOwnership();

        return localRow;
    }

    await refreshOwnership();

    return data;
}

// =====================================================
// Purchases — admin
// =====================================================

export async function loadAllPurchases() {
    const {
        data,
        error
    } = await supabase
        .from("course_purchases")
        .select("*")
        .order("created_at", { ascending: false });

    if (error) {
        if (!isMissingTable(error)) {
            console.error("Admin purchases loading error:", error);
        }

        return {
            rows: localRead(PURCHASES_STORAGE_KEY, []),
            mode: "local"
        };
    }

    return { rows: data || [], mode: "cloud" };
}

export async function setPurchaseStatus(purchaseId, status) {
    const normalized = status === "approved" ? "approved" : "rejected";
    const nowIso = new Date().toISOString();

    // Look up which course this purchase unlocks so the
    // approval can stamp the course validity (expiry).
    const {
        data: existing,
        error: fetchError
    } = await supabase
        .from("course_purchases")
        .select("course_id")
        .eq("id", purchaseId)
        .maybeSingle();

    if (fetchError && !isMissingTable(fetchError)) {
        throw fetchError;
    }

    const expiresAt =
        normalized === "approved"
            ? await computeExpiry(existing?.course_id)
            : null;

    const {
        data,
        error
    } = await supabase
        .from("course_purchases")
        .update({
            status: normalized,
            reviewed_at: nowIso,
            expires_at: expiresAt
        })
        .eq("id", purchaseId)
        .select()
        .single();

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const all = localRead(PURCHASES_STORAGE_KEY, []);
        const index = all.findIndex(
            row => String(row.id) === String(purchaseId)
        );

        if (index === -1) {
            throw new Error("Purchase request not found.");
        }

        const localExpires =
            normalized === "approved"
                ? await computeExpiry(
                    existing?.course_id || all[index].course_id
                )
                : null;

        all[index] = {
            ...all[index],
            status: normalized,
            reviewed_at: nowIso,
            expires_at: localExpires
        };

        localWrite(PURCHASES_STORAGE_KEY, all);

        return all[index];
    }

    return data;
}

// Course validity: an approval unlocks content for the
// course's validity_days window.
async function computeExpiry(courseId) {
    const courses = await loadCourses();

    const course = courses.find(
        item => String(item.id) === String(courseId)
    );

    const days = Number(course?.validity_days);

    return new Date(
        Date.now() + (days > 0 ? days : 365) * 86400000
    ).toISOString();
}

// Admin: push the expiry further out (extend course expiry).
export async function extendPurchaseExpiry(purchaseId, days = 30) {
    const amount = Number(days) > 0 ? Number(days) : 30;

    const {
        data: existing,
        error: fetchError
    } = await supabase
        .from("course_purchases")
        .select("*")
        .eq("id", purchaseId)
        .maybeSingle();

    if (fetchError && !isMissingTable(fetchError)) {
        throw fetchError;
    }

    const baseFor = row =>
        row?.expires_at
            ? Math.max(new Date(row.expires_at).getTime(), Date.now())
            : Date.now();

    const newExpiry = base =>
        new Date(base + amount * 86400000).toISOString();

    if (existing) {
        const {
            data,
            error
        } = await supabase
            .from("course_purchases")
            .update({ expires_at: newExpiry(baseFor(existing)) })
            .eq("id", purchaseId)
            .select()
            .single();

        if (error) {
            throw error;
        }

        return data;
    }

    const all = localRead(PURCHASES_STORAGE_KEY, []);
    const index = all.findIndex(
        row => String(row.id) === String(purchaseId)
    );

    if (index === -1) {
        throw new Error("Purchase not found.");
    }

    all[index] = {
        ...all[index],
        expires_at: newExpiry(baseFor(all[index]))
    };

    localWrite(PURCHASES_STORAGE_KEY, all);

    return all[index];
}

// Admin: remove a purchase record entirely.
export async function deletePurchase(purchaseId) {
    const {
        error
    } = await supabase
        .from("course_purchases")
        .delete()
        .eq("id", purchaseId);

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const all = localRead(PURCHASES_STORAGE_KEY, []);

        localWrite(
            PURCHASES_STORAGE_KEY,
            all.filter(row => String(row.id) !== String(purchaseId))
        );
    }
}

// =====================================================
// UPI ID (site_settings, localStorage fallback)
// =====================================================

export async function getUpiId() {
    const {
        data,
        error
    } = await supabase
        .from("site_settings")
        .select("upi_id")
        .eq("id", 1)
        .maybeSingle();

    if (!error && data?.upi_id) {
        return data.upi_id;
    }

    const localValue = localRead(UPi_STORAGE_KEY, null);

    return localValue || DEFAULT_UPi_ID;
}

export async function saveUpiId(value) {
    const trimmed = String(value || "").trim();

    if (!trimmed) {
        throw new Error("UPI ID cannot be empty.");
    }

    const {
        error
    } = await supabase
        .from("site_settings")
        .update({ upi_id: trimmed })
        .eq("id", 1);

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        localWrite(UPi_STORAGE_KEY, trimmed);

        return { mode: "local", upiId: trimmed };
    }

    localWrite(UPi_STORAGE_KEY, trimmed);

    return { mode: "cloud", upiId: trimmed };
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
        option.textContent = "Available after running sql/setup.sql";
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
                </div>
                <div class="mt-4">
                    ${action}
                </div>
            </article>
        `;
    }).join("");
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
            ? "Storage: this browser only — run sql/setup.sql for database storage."
            : "Storage: Supabase database";
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
                        class="extend-purchase-btn rounded-lg bg-blue-50 hover:bg-blue-100
                               text-blue-700 text-xs font-bold px-3 py-2"
                        title="Extend course expiry by 30 days"
                        data-purchase-id="${escapeHtml(String(row.id))}">
                        <i class="fa-solid fa-calendar-plus mr-1"></i>Extend 30 days
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
        ".extend-purchase-btn, .delete-purchase-btn"
    );

    if (!button) {
        return;
    }

    const purchaseId = button.dataset.purchaseId;
    const approved = button.classList.contains("approve-purchase-btn");
    const extended = button.classList.contains("extend-purchase-btn");
    const removed = button.classList.contains("delete-purchase-btn");

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

async function handleUpiSave() {
    const input = $("upiIdInput");

    if (!input) {
        return;
    }

    try {
        const result = await saveUpiId(input.value);

        purchasesAdminMessage(
            result.mode === "local"
                ? "UPI ID saved (local — run sql/setup.sql for database storage)."
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
    }

    // ---------------------------------------------
    // admin.html — payment approvals
    // ---------------------------------------------
    if ($("purchaseRequestsList")) {
        renderAdminPurchases().catch(error => {
            console.error("Admin purchases render error:", error);
        });

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
