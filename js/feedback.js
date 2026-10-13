// =====================================================
// TechCampus_Hub
// feedback.js
//
// Feedback & rating: 1–5 star ratings plus written
// feedback for the website, a course or a note, the
// student's own submissions, and the admin inbox
// (view / delete).
//
// Storage: Supabase `feedback` table created by
// sql/setup.sql (Part 2). When the table is missing the
// module falls back to this-browser-only storage so the
// feature still works before the migration is run.
// =====================================================

import { supabase } from "../supabase.js";
import { $, escapeHtml, formatDate } from "./helpers.js";
import { currentStudent, currentUserRole } from "./state.js";
import { loadCourses } from "./courses.js";
import { loadNotes, notes } from "./data.js";

// =====================================================
// Constants
// =====================================================

const LOCAL_KEY = "campusnotes:feedback";

const TARGET_TYPES = ["website", "course", "note"];

// =====================================================
// Module state
// =====================================================

let myFeedbackCache = [];
let eventsWired = false;
let selectedRating = 0;

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

export function starHtml(rating) {
    const value = Math.max(1, Math.min(5, Number(rating) || 0));

    let html = "";

    for (let star = 1; star <= 5; star += 1) {
        html += star <= value
            ? `<i class="fa-solid fa-star text-amber-400"></i>`
            : `<i class="fa-regular fa-star text-slate-300"></i>`;
    }

    return html;
}

function pageMessage(text, type = "info") {
    const box = $("feedbackPageMessage");

    if (!box) {
        return;
    }

    box.textContent = text || "";
    box.classList.toggle("hidden", !text);
    box.className = "text-sm min-h-5 mb-4 px-4 py-3 rounded-xl";

    if (!text) {
        box.classList.add("hidden");
        return;
    }

    if (type === "error") {
        box.classList.add("bg-red-50", "text-red-700");
    } else {
        box.classList.add("bg-emerald-50", "text-emerald-700");
    }
}

// =====================================================
// Storage
// =====================================================

export async function submitFeedback({
    targetType,
    targetLabel,
    rating,
    message
}) {
    const identity = await getIdentity();

    if (!identity.id) {
        throw new Error("Please log in before submitting feedback.");
    }

    const normalizedType = TARGET_TYPES.includes(targetType)
        ? targetType
        : "website";

    const normalizedRating = Number(rating);

    if (
        !Number.isInteger(normalizedRating) ||
        normalizedRating < 1 ||
        normalizedRating > 5
    ) {
        throw new Error("Please choose a star rating from 1 to 5.");
    }

    const row = {
        user_id: identity.id,
        user_name: identity.name,
        user_email: identity.email,
        target_type: normalizedType,
        target_label: String(targetLabel || "Website").trim(),
        rating: normalizedRating,
        message: String(message || "").trim(),
        created_at: new Date().toISOString()
    };

    const {
        data,
        error
    } = await supabase
        .from("feedback")
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

        const all = localRead(LOCAL_KEY, []);
        all.unshift(localRow);
        localWrite(LOCAL_KEY, all);

        myFeedbackCache.unshift(localRow);

        return { row: localRow, mode: "local" };
    }

    myFeedbackCache.unshift(data);

    return { row: data, mode: "cloud" };
}

export async function loadMyFeedback(force = false) {
    if (!force && myFeedbackCache.length) {
        return myFeedbackCache;
    }

    const identity = await getIdentity();

    if (!identity.id) {
        myFeedbackCache = [];
        return myFeedbackCache;
    }

    const {
        data,
        error
    } = await supabase
        .from("feedback")
        .select("*")
        .eq("user_id", identity.id)
        .order("created_at", { ascending: false });

    if (error) {
        if (!isMissingTable(error)) {
            console.error("Feedback loading error:", error);
        }

        myFeedbackCache = localRead(LOCAL_KEY, []).filter(
            row => String(row.user_id) === String(identity.id)
        );

        return myFeedbackCache;
    }

    myFeedbackCache = data || [];

    return myFeedbackCache;
}

export async function loadAllFeedback() {
    const {
        data,
        error
    } = await supabase
        .from("feedback")
        .select("*")
        .order("created_at", { ascending: false });

    if (error) {
        if (!isMissingTable(error)) {
            console.error("Admin feedback loading error:", error);
        }

        return { rows: localRead(LOCAL_KEY, []), mode: "local" };
    }

    return { rows: data || [], mode: "cloud" };
}

export async function deleteFeedback(feedbackId) {
    const {
        error
    } = await supabase
        .from("feedback")
        .delete()
        .eq("id", feedbackId);

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const all = localRead(LOCAL_KEY, []);

        localWrite(
            LOCAL_KEY,
            all.filter(row => String(row.id) !== String(feedbackId))
        );
    }

    myFeedbackCache = myFeedbackCache.filter(
        row => String(row.id) !== String(feedbackId)
    );
}

// =====================================================
// Student page (feedback.html)
// =====================================================

function setRating(value) {
    const numeric = Number(value) || 0;

    selectedRating = Math.max(0, Math.min(5, numeric));

    const input = $("feedbackRating");

    if (input) {
        input.value = String(selectedRating);
    }

    document.querySelectorAll("#feedbackStars .star-btn").forEach(button => {
        button.classList.toggle(
            "active",
            Number(button.dataset.rating) <= selectedRating
        );
    });

    const hint = $("feedbackRatingHint");

    if (hint) {
        hint.textContent = selectedRating
            ? `${selectedRating} star${selectedRating > 1 ? "s" : ""} selected`
            : "Tap a star to rate";
    }
}

async function populateTargetSelect(targetType) {
    const group = $("feedbackTargetGroup");
    const select = $("feedbackTargetSelect");

    if (!group || !select) {
        return;
    }

    group.classList.toggle("hidden", targetType === "website");

    if (targetType === "website") {
        return;
    }

    select.innerHTML = `<option value="">Select…</option>`;

    if (targetType === "course") {
        const courses = await loadCourses();

        courses.forEach(course => {
            const option = document.createElement("option");
            option.value = course.title;
            option.textContent = course.title;
            select.append(option);
        });

        return;
    }

    // Notes
    if (!notes.length) {
        try {
            await loadNotes();
        } catch (error) {
            console.error("Notes loading error:", error);
        }
    }

    notes.forEach(note => {
        const label = note.title || note.subject || "Untitled note";

        const option = document.createElement("option");
        option.value = label;
        option.textContent = label;
        select.append(option);
    });
}

async function renderMyFeedback() {
    const list = $("myFeedbackList");

    if (!list) {
        return;
    }

    const rows = await loadMyFeedback(true);

    if (!rows.length) {
        list.innerHTML = `
            <p class="text-sm text-slate-500">
                No feedback submitted yet.
            </p>
        `;
        return;
    }

    list.innerHTML = rows.map(row => `
        <div class="border rounded-xl p-3">
            <div class="flex items-center justify-between gap-2">
                <span class="text-xs font-bold uppercase text-slate-400">
                    ${escapeHtml(row.target_label || row.target_type || "Website")}
                </span>
                <span>${starHtml(row.rating)}</span>
            </div>
            ${row.message
                ? `<p class="text-sm text-slate-600 mt-1">
                       ${escapeHtml(row.message)}
                   </p>`
                : ""}
            <p class="text-xs text-slate-400 mt-1">
                ${escapeHtml(formatDate(row.created_at))}
            </p>
        </div>
    `).join("");
}

async function handleFeedbackSubmit(event) {
    event.preventDefault();

    const targetType = $("feedbackTargetType")?.value || "website";
    const targetSelect = $("feedbackTargetSelect");

    let targetLabel = "Website";

    if (targetType !== "website") {
        targetLabel = targetSelect?.value || "";

        if (!targetLabel) {
            pageMessage("Please choose what you want to rate.", "error");
            return;
        }
    }

    try {
        const result = await submitFeedback({
            targetType,
            targetLabel,
            rating: selectedRating,
            message: $("feedbackMessage")?.value || ""
        });

        pageMessage(
            result.mode === "local"
                ? "Feedback saved (local — run sql/setup.sql for database storage)."
                : "Thank you! Your feedback has been submitted.",
            "success"
        );

        const form = $("feedbackForm");
        form?.reset();
        setRating(0);
        await populateTargetSelect("website");

        await renderMyFeedback();
    } catch (error) {
        console.error("Feedback submit error:", error);

        pageMessage(
            error?.message || "Could not submit your feedback.",
            "error"
        );
    }
}

// =====================================================
// Admin — feedback inbox (admin.html)
// =====================================================

function adminMessage(text, type = "info") {
    const box = $("feedbackAdminMessage");

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

async function renderAdminFeedbackList() {
    const list = $("adminFeedbackList");

    if (!list) {
        return;
    }

    const { rows } = await loadAllFeedback();

    if (!rows.length) {
        list.innerHTML = `
            <p class="text-sm text-slate-500">No feedback received yet.</p>
        `;
        return;
    }

    list.innerHTML = rows.map(row => `
        <div class="border rounded-xl p-4 flex flex-wrap items-start gap-4 justify-between">
            <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                    <p class="font-bold text-slate-800">
                        ${escapeHtml(row.user_name || row.user_email || "Student")}
                    </p>
                    <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-500">
                        ${escapeHtml(row.target_label || row.target_type || "Website")}
                    </span>
                    <span>${starHtml(row.rating)}</span>
                </div>
                ${row.message
                    ? `<p class="text-sm text-slate-600 mt-1">
                           ${escapeHtml(row.message)}
                       </p>`
                    : ""}
                <p class="text-xs text-slate-400 mt-1">
                    ${escapeHtml(row.user_email || "")}
                    ${row.created_at
                        ? ` · ${escapeHtml(formatDate(row.created_at))}`
                        : ""}
                </p>
            </div>
            <button type="button"
                class="delete-feedback-btn rounded-lg bg-red-50 hover:bg-red-100
                       text-red-600 text-xs font-bold px-3 py-2"
                data-feedback-id="${escapeHtml(String(row.id))}">
                Delete
            </button>
        </div>
    `).join("");
}

// =====================================================
// Events (wired once from events.js)
// =====================================================

export function setupFeedbackEvents() {
    if (eventsWired) {
        return;
    }

    eventsWired = true;

    // Star rating widget.
    $("feedbackStars")?.addEventListener("click", event => {
        const button = event.target.closest(".star-btn");

        if (!button) {
            return;
        }

        setRating(button.dataset.rating);
    });

    // Target type switches the picker between website/course/note.
    $("feedbackTargetType")?.addEventListener("change", event => {
        populateTargetSelect(event.target.value).catch(error => {
            console.error("Feedback target populate error:", error);
        });
    });

    $("feedbackForm")?.addEventListener("submit", handleFeedbackSubmit);

    // Admin: delete a feedback entry.
    $("adminFeedbackList")?.addEventListener("click", async event => {
        const button = event.target.closest(".delete-feedback-btn");

        if (!button) {
            return;
        }

        const confirmed = window.confirm(
            "Delete this feedback entry?"
        );

        if (!confirmed) {
            return;
        }

        button.disabled = true;

        try {
            await deleteFeedback(button.dataset.feedbackId);

            adminMessage("Feedback deleted.", "success");

            await renderAdminFeedbackList();
        } catch (error) {
            console.error("Feedback delete error:", error);

            adminMessage(
                error?.message || "Could not delete the feedback.",
                "error"
            );

            button.disabled = false;
        }
    });

    $("refreshFeedbackBtn")?.addEventListener("click", () => {
        renderAdminFeedbackList().catch(error => {
            console.error("Admin feedback refresh error:", error);
        });
    });
}

// Called once the app knows who is logged in.
export async function initFeedback() {
    setupFeedbackEvents();

    if ($("feedbackForm")) {
        setRating(0);
        await renderMyFeedback();
    }

    if ($("adminFeedbackList")) {
        await renderAdminFeedbackList();
    }
}
