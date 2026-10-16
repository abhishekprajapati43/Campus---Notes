// =====================================================
// TechCampus_Hub
// faculty.js
//
// Faculty workspace (faculty.html):
//   - launch a course        POST /api/faculty/courses
//   - list own courses       GET  /api/faculty/courses
//   - upload a note (PDF)    POST /api/faculty/notes
//   - list own notes         GET  /api/faculty/notes
//   - edit own note          PUT  /api/faculty/notes/{id}
//
// There is NO removal route under /api/faculty and this
// module therefore offers no removal affordance at all -
// removing content stays behind the admin endpoints.
//
// PDFs travel straight from the browser to Cloudinary
// through js/storage.js uploadPdf(), the same pipeline
// the admin panel uses; only the resulting URL/public id
// is persisted on the note record.
// =====================================================

import { $, escapeHtml, formatDate } from "./helpers.js";
import { currentStudent, currentUserRole } from "./state.js";

import {
    apiGet,
    apiPost,
    apiPut,
    getStoredUser,
    normalizeCourse
} from "./api.js";

import { uploadPdf } from "./storage.js";
import { formatPrice } from "./courses.js";

// =====================================================
// Module state
// =====================================================

let eventsWired = false;

// Rows exactly as the backend returns them (camelCase).
let facultyCourses = [];
let facultyNotes = [];

// One auto-hide timer per message element.
const messageTimers = {};

// Cloudinary folder used for faculty PDFs (mirrors the
// admin flow, which uploads to the "notes" folder).
const PDF_FOLDER = "notes";

// =====================================================
// Small field helpers
// =====================================================

function getValue(id) {
    return String($(id)?.value || "").trim();
}

function getNumber(id) {
    const raw = getValue(id);

    if (raw === "") {
        return null;
    }

    const number = Number(raw);

    return Number.isFinite(number) ? number : null;
}

function getFile(id) {
    return $(id)?.files?.[0] || null;
}

function isFacultyUser() {
    return currentUserRole === "faculty";
}

// =====================================================
// Message elements
// =====================================================
//
// Same look/behaviour as the admin message element:
// textContent only, success/error class toggle, auto-hide.
// =====================================================

function facultyMessage(elementId, message, type = "success", hideAfter = 5000) {
    const container = $(elementId);

    if (!container) {
        if (message && type === "error") {
            console.error(message);
        }

        return;
    }

    window.clearTimeout(messageTimers[elementId]);
    messageTimers[elementId] = null;

    if (!message) {
        container.classList.add("hidden");
        container.textContent = "";
        return;
    }

    container.textContent = String(message);
    container.className = "mb-4 rounded-xl px-4 py-3 text-sm";

    if (type === "error") {
        container.classList.add(
            "bg-red-50",
            "text-red-700",
            "border",
            "border-red-200"
        );
    } else {
        container.classList.add(
            "bg-green-50",
            "text-green-700",
            "border",
            "border-green-200"
        );
    }

    container.classList.remove("hidden");

    if (hideAfter > 0) {
        messageTimers[elementId] = window.setTimeout(() => {
            container.classList.add("hidden");
        }, hideAfter);
    }
}

function errorMessage(error, fallback) {
    return error?.message || fallback;
}

function dispatchRefresh() {
    window.dispatchEvent(
        new CustomEvent("campusnotes:refresh")
    );
}

// =====================================================
// Shared empty state (dashed card, render.js idiom)
// =====================================================

function emptyStateHtml(iconClass, message) {
    return `
        <div class="col-span-full">
            <div class="rounded-2xl border border-dashed
                        border-gray-300 bg-white p-10
                        text-center shadow-sm">
                <div class="mx-auto mb-4 flex h-14 w-14
                            items-center justify-center
                            rounded-full bg-gray-100">
                    <i class="fa-solid ${iconClass} text-slate-400 text-xl"></i>
                </div>
                <p class="text-sm text-slate-500">${escapeHtml(message)}</p>
            </div>
        </div>
    `;
}

// =====================================================
// Welcome header
// =====================================================

function renderWelcome() {
    const target = $("facultyWelcomeName");

    if (!target) {
        return;
    }

    const user = getStoredUser() || null;
    const name = String(
        user?.name || currentStudent?.name || ""
    ).trim();

    target.textContent = name || "Faculty!";
}

// =====================================================
// Courses (GET /api/faculty/courses)
// =====================================================

async function loadFacultyCourses() {
    const rows = await apiGet("/api/faculty/courses");

    facultyCourses = (Array.isArray(rows) ? rows : [])
        .map(normalizeCourse)
        .filter(Boolean);

    renderFacultyCourses();
    populateBundleSelect();
}

function renderFacultyCourses() {
    const list = $("facultyCoursesList");

    if (!list) {
        return;
    }

    if (!facultyCourses.length) {
        list.innerHTML = emptyStateHtml(
            "fa-rocket",
            "You haven't launched a course yet."
        );
        return;
    }

    list.innerHTML = facultyCourses.map(course => {
        const price = formatPrice(course.price_inr);
        const compareAt = Number(course.mrp_inr) > Number(course.price_inr)
            ? formatPrice(course.mrp_inr)
            : "";
        const validity = Number(course.validity_days) > 0
            ? `${Number(course.validity_days)} days validity`
            : "";
        const meta = [
            course.code || "",
            course.category || ""
        ].filter(Boolean).join(" · ");

        return `
            <article class="resource-card rounded-2xl border border-gray-200
                            bg-white p-5 shadow-sm">
                <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                        <h4 class="truncate text-base font-semibold text-gray-900"
                            title="${escapeHtml(course.title || "Untitled course")}">
                            ${escapeHtml(course.title || "Untitled course")}
                        </h4>
                        <p class="mt-1 text-xs text-slate-400">
                            ${escapeHtml(meta)}
                        </p>
                    </div>
                    <span class="shrink-0 rounded-full bg-emerald-50 px-2.5 py-1
                                 text-xs font-bold text-emerald-700">
                        Live
                    </span>
                </div>
                <div class="mt-4 flex flex-wrap items-center gap-2">
                    <span class="text-base font-extrabold text-blue-600">
                        ${escapeHtml(price)}
                    </span>
                    ${compareAt ? `
                    <span class="text-xs text-slate-400 line-through">
                        ${escapeHtml(compareAt)}
                    </span>` : ""}
                    ${validity ? `
                    <span class="text-xs text-slate-400">
                        ${escapeHtml(validity)}
                    </span>` : ""}
                </div>
            </article>
        `;
    }).join("");
}

// "My courses" picker for the optional note bundle.
function populateBundleSelect() {
    const select = $("noteBundleSelect");

    if (!select) {
        return;
    }

    const previous = select.value;

    const options = facultyCourses.map(course => `
        <option value="${escapeHtml(String(course.id ?? ""))}">
            ${escapeHtml(course.title || "Untitled course")}
        </option>
    `).join("");

    select.innerHTML = `
        <option value="">
            Free — no bundle
        </option>
        ${options}
    `;

    const canRestore = Array.from(select.options).some(
        option => option.value === previous
    );

    select.value = canRestore ? previous : "";
}

// =====================================================
// Notes (GET /api/faculty/notes)
// =====================================================

async function loadFacultyNotes() {
    const rows = await apiGet("/api/faculty/notes");

    facultyNotes = Array.isArray(rows) ? rows : [];

    renderFacultyNotes();
}

function noteMetaLine(note) {
    const parts = [
        note.subject || "",
        note.course || ""
    ].filter(Boolean);

    if (Number(note.year) > 0) {
        parts.push(`Year ${Number(note.year)}`);
    }

    if (Number(note.semester) > 0) {
        parts.push(`Semester ${Number(note.semester)}`);
    }

    return parts.join(" · ");
}

function renderFacultyNotes() {
    const list = $("facultyNotesList");

    if (!list) {
        return;
    }

    if (!facultyNotes.length) {
        list.innerHTML = emptyStateHtml(
            "fa-file-lines",
            "You haven't uploaded any notes yet."
        );
        return;
    }

    list.innerHTML = facultyNotes.map(note => {
        const fileUrl = typeof note.fileUrl === "string"
            ? note.fileUrl.trim()
            : "";

        // View only exists when there is a stored PDF.
        const canView = /^https?:\/\//i.test(fileUrl);

        const viewHtml = canView ? `
                    <a href="${escapeHtml(fileUrl)}"
                       target="_blank"
                       rel="noopener noreferrer"
                       class="inline-flex items-center gap-2 rounded-lg bg-blue-50
                              px-3 py-2 text-sm font-medium text-blue-700 transition
                              hover:bg-blue-100">
                        <i class="fa-solid fa-arrow-up-right-from-square"></i>
                        View
                    </a>` : "";

        const bundleBadge = note.courseTitle ? `
                <span class="mt-3 inline-flex rounded-full bg-violet-50 px-2.5 py-1
                             text-xs font-bold text-violet-700">
                    ${escapeHtml(note.courseTitle)}
                </span>` : "";

        return `
            <article data-faculty-note-card
                class="resource-card rounded-2xl border border-gray-200
                       bg-white p-5 shadow-sm">
                <div class="flex items-start gap-3">
                    <div class="flex h-11 w-11 shrink-0 items-center justify-center
                                rounded-xl bg-blue-50 text-blue-600">
                        <i class="fa-regular fa-file-lines"></i>
                    </div>
                    <div class="min-w-0">
                        <h3 class="truncate text-base font-semibold text-gray-900"
                            title="${escapeHtml(note.title || "Untitled note")}">
                            ${escapeHtml(note.title || "Untitled note")}
                        </h3>
                        <p class="mt-1 text-xs text-slate-400">
                            ${escapeHtml(noteMetaLine(note))}
                        </p>
                        <p class="mt-1 text-xs text-slate-400">
                            Uploaded ${escapeHtml(formatDate(note.createdAt))}
                        </p>
                    </div>
                </div>
                ${bundleBadge}
                <div class="mt-4 flex flex-wrap items-center gap-2">
                    ${viewHtml}
                    <button type="button"
                        class="faculty-edit-btn inline-flex items-center gap-2
                               rounded-lg bg-slate-100 px-3 py-2 text-sm font-medium
                               text-slate-700 transition hover:bg-slate-200"
                        data-note-id="${escapeHtml(String(note.id ?? ""))}">
                        <i class="fa-solid fa-pen"></i>
                        Edit
                    </button>
                </div>
            </article>
        `;
    }).join("");
}

function findNote(noteId) {
    const wanted = String(noteId);

    return facultyNotes.find(
        note => String(note.id) === wanted
    ) || null;
}

// =====================================================
// Edit modal (own markup in faculty.html)
// =====================================================

function openEditModal(note) {
    const modal = $("facultyEditModal");

    if (!modal || !note) {
        return;
    }

    const idField = $("facultyEditNoteId");
    const titleField = $("facultyEditTitle");
    const subjectField = $("facultyEditSubject");
    const descriptionField = $("facultyEditDescription");
    const fileField = $("facultyEditFile");

    if (idField) {
        idField.value = String(note.id ?? "");
    }

    if (titleField) {
        titleField.value = note.title || "";
    }

    if (subjectField) {
        subjectField.value = note.subject || "";
    }

    if (descriptionField) {
        descriptionField.value = note.description || "";
    }

    if (fileField) {
        fileField.value = "";
    }

    facultyMessage("facultyEditMessage", "");

    modal.classList.remove("hidden");
    modal.classList.add("flex");

    subjectField?.focus();
}

function closeEditModal() {
    const modal = $("facultyEditModal");

    if (!modal) {
        return;
    }

    modal.classList.add("hidden");
    modal.classList.remove("flex");

    $("facultyEditForm")?.reset();
    facultyMessage("facultyEditMessage", "");
}

// =====================================================
// Launch course — POST /api/faculty/courses
// =====================================================

async function handleLaunchCourse(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const button = form?.querySelector('button[type="submit"]');

    const title = getValue("launchCourseTitle");
    const price = getNumber("launchCoursePrice");

    if (!title) {
        facultyMessage(
            "launchCourseMessage",
            "Please enter the course title.",
            "error"
        );
        return;
    }

    if (price === null || price < 0) {
        facultyMessage(
            "launchCourseMessage",
            "Please enter a valid price (0 or more).",
            "error"
        );
        return;
    }

    const mrp = getNumber("launchCourseMrp");
    const validity = getNumber("launchCourseValidity");
    const highlights = getValue("launchCourseHighlights")
        .split("\n")
        .map(line => line.trim())
        .filter(Boolean);

    const payload = {
        title,
        code: getValue("launchCourseCode") || null,
        category: getValue("launchCourseCategory") || null,
        description: getValue("launchCourseDescription") || null,
        priceInr: price,
        // No compare-at price when the field is blank or not a discount.
        mrpInr: mrp !== null && mrp > price ? mrp : null,
        validityDays: validity !== null && validity >= 1 ? validity : 365,
        highlights: highlights.length ? highlights : null
    };

    if (button) {
        button.disabled = true;
    }

    try {
        const created = await apiPost(
            "/api/faculty/courses",
            payload
        );

        facultyMessage(
            "launchCourseMessage",
            "Course launched successfully. It is live now."
        );

        form?.reset();

        await loadFacultyCourses();

        if (created) {
            dispatchRefresh();
        }
    } catch (error) {
        console.error("Launch course error:", error);

        facultyMessage(
            "launchCourseMessage",
            errorMessage(error, "Could not launch the course."),
            "error"
        );
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

// =====================================================
// Upload note — uploadPdf() then POST /api/faculty/notes
// =====================================================

async function handleUploadNote(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const button = $("uploadNoteBtn");

    const subject = getValue("facultyNoteSubject");
    const file = getFile("facultyNoteFile");

    if (!subject) {
        facultyMessage(
            "uploadNoteMessage",
            "Please enter the subject.",
            "error"
        );
        return;
    }

    if (!file) {
        facultyMessage(
            "uploadNoteMessage",
            "Please select a PDF file.",
            "error"
        );
        return;
    }

    if (button) {
        button.disabled = true;
    }

    facultyMessage(
        "uploadNoteMessage",
        "Uploading PDF to Cloudinary…",
        "success",
        0
    );

    try {
        // Browser -> Cloudinary (same pipeline as admin).
        const uploaded = await uploadPdf(file, PDF_FOLDER);

        const fileUrl = uploaded?.file_url || null;
        const storagePath = uploaded?.cloudinary_public_id || null;

        if (!fileUrl) {
            throw new Error(
                "Upload succeeded, but no file URL was returned."
            );
        }

        const payload = {
            title: getValue("facultyNoteTitle") || null,
            subject,
            course: getValue("facultyNoteCourse") || null,
            branch: getValue("facultyNoteBranch") || null,
            year: getNumber("facultyNoteYear"),
            semester: getNumber("facultyNoteSemester"),
            description: getValue("facultyNoteDescription") || null,
            fileUrl,
            storagePath,
            // Optional bundle this note unlocks.
            courseId: getNumber("noteBundleSelect")
        };

        await apiPost("/api/faculty/notes", payload);

        form?.reset();

        facultyMessage(
            "uploadNoteMessage",
            "Note uploaded successfully."
        );

        await loadFacultyNotes();
        dispatchRefresh();
    } catch (error) {
        console.error("Faculty note upload error:", error);

        facultyMessage(
            "uploadNoteMessage",
            errorMessage(error, "Could not upload the note."),
            "error"
        );
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

// =====================================================
// Edit note — optional uploadPdf() then PUT /api/faculty/notes/{id}
// =====================================================

async function handleEditSubmit(event) {
    event.preventDefault();

    const noteId = $("facultyEditNoteId")?.value;

    if (!noteId) {
        return;
    }

    const button = $("saveFacultyEditBtn");

    const file = getFile("facultyEditFile");

    if (button) {
        button.disabled = true;
    }

    if (file) {
        facultyMessage(
            "facultyEditMessage",
            "Uploading PDF to Cloudinary…",
            "success",
            0
        );
    } else {
        facultyMessage(
            "facultyEditMessage",
            "Saving changes…",
            "success",
            0
        );
    }

    try {
        // Blank fields are sent as null so the backend keeps
        // the current values (partial PUT in NoteService).
        const payload = {
            title: getValue("facultyEditTitle") || null,
            subject: getValue("facultyEditSubject") || null,
            description: getValue("facultyEditDescription") || null
        };

        if (file) {
            const uploaded = await uploadPdf(file, PDF_FOLDER);

            const fileUrl = uploaded?.file_url || null;

            if (!fileUrl) {
                throw new Error(
                    "Upload succeeded, but no file URL was returned."
                );
            }

            payload.fileUrl = fileUrl;
            payload.storagePath =
                uploaded?.cloudinary_public_id || "";
        }

        await apiPut(
            `/api/faculty/notes/${encodeURIComponent(noteId)}`,
            payload
        );

        closeEditModal();

        facultyMessage(
            "facultyManageMessage",
            "Note updated successfully."
        );

        await loadFacultyNotes();
        dispatchRefresh();
    } catch (error) {
        console.error("Faculty note edit error:", error);

        facultyMessage(
            "facultyEditMessage",
            errorMessage(error, "Could not update the note."),
            "error"
        );
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

// =====================================================
// Events (wired once, null-guarded like other modules)
// =====================================================

function handleNotesListClick(event) {
    const button = event.target.closest(".faculty-edit-btn");

    if (!button) {
        return;
    }

    const note = findNote(button.dataset.noteId);

    if (!note) {
        return;
    }

    openEditModal(note);
}

export function setupFacultyEvents() {
    if (eventsWired) {
        return;
    }

    eventsWired = true;

    $("launchCourseForm")?.addEventListener(
        "submit",
        handleLaunchCourse
    );

    $("uploadNoteForm")?.addEventListener(
        "submit",
        handleUploadNote
    );

    $("facultyEditForm")?.addEventListener(
        "submit",
        handleEditSubmit
    );

    $("closeFacultyEditBtn")?.addEventListener(
        "click",
        closeEditModal
    );

    $("cancelFacultyEditBtn")?.addEventListener(
        "click",
        closeEditModal
    );

    $("facultyNotesList")?.addEventListener(
        "click",
        handleNotesListClick
    );

    // Click on the dimmed backdrop closes the modal.
    $("facultyEditModal")?.addEventListener(
        "click",
        event => {
            if (event.target === event.currentTarget) {
                closeEditModal();
            }
        }
    );

    document.addEventListener("keydown", event => {
        if (event.key !== "Escape") {
            return;
        }

        const modal = $("facultyEditModal");

        if (modal && !modal.classList.contains("hidden")) {
            closeEditModal();
        }
    });
}

// =====================================================
// Entry point (called from app.js on protected pages)
// =====================================================

export async function initFaculty() {
    // Every other page keeps its own DOM untouched.
    if (document.body?.dataset?.page !== "faculty") {
        return;
    }

    renderWelcome();
    setupFacultyEvents();

    // Light guard: data only loads for the faculty role
    // (applyRoleUI already redirects everyone else away).
    if (!isFacultyUser()) {
        return;
    }

    try {
        await loadFacultyCourses();
    } catch (error) {
        console.error("Faculty courses loading error:", error);

        facultyMessage(
            "launchCourseMessage",
            errorMessage(error, "Could not load your courses."),
            "error"
        );
    }

    try {
        await loadFacultyNotes();
    } catch (error) {
        console.error("Faculty notes loading error:", error);

        facultyMessage(
            "facultyManageMessage",
            errorMessage(error, "Could not load your notes."),
            "error"
        );
    }
}
