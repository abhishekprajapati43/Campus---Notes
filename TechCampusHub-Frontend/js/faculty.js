// =====================================================
// TechCampus_Hub
// faculty.js
//
// Faculty workspace (faculty.html):
//   - launch a course          POST /api/faculty/courses
//   - list own courses         GET  /api/faculty/courses
//   - upload content (PDF)     POST /api/faculty/notes | tnp-notes | pyqs | timetables
//   - list own content         GET  /api/faculty/notes | tnp-notes | pyqs | timetables
//   - edit own content         PUT  /api/faculty/notes/{id} | tnp-notes/{id} | pyqs/{id} | timetables/{id}
//
// There is NO removal route under /api/faculty and this
// module therefore offers no removal affordance at all -
// removing content stays behind the admin endpoints.
//
// PDFs travel straight from the browser to Cloudinary
// through js/storage.js uploadPdf(), the same pipeline
// the admin panel uses; only the resulting URL/public id
// is persisted on the content record.
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

// The other three content collections (owner-scoped, same
// shape as the admin rows).
let facultyTnpItems = [];
let facultyPyqItems = [];
let facultyTimetableItems = [];

// Active category in the Manage Content pill row.
let activeManageCategory = "notes";

// One auto-hide timer per message element.
const messageTimers = {};

// Cloudinary folder used for faculty PDFs (mirrors the
// admin flow, which uploads to the "notes" folder).
const PDF_FOLDER = "notes";

// Per-category Cloudinary folders, mirroring the admin
// panel ("notes", "tnp", "pyq", "timetable" folders).
const CONTENT_FOLDERS = {
    notes: PDF_FOLDER,
    tnp: "tnp",
    pyq: "pyq",
    timetable: "timetable"
};

// Short labels used in headings, success messages and
// error fallbacks.
const CATEGORY_LABELS = {
    notes: "Note",
    tnp: "TNP resource",
    pyq: "PYQ",
    timetable: "Timetable"
};

// Section subtitle shown for the selected upload category.
const UPLOAD_SUBTITLES = {
    notes: "Share a PDF with your students.",
    tnp: "Share placement resources with your students.",
    pyq: "Share previous year papers with your students.",
    timetable: "Share exam timetables with your students."
};

// One message element per upload form, so a message for one
// category can never show up under another one.
const UPLOAD_MESSAGE_IDS = {
    notes: "uploadNoteMessage",
    tnp: "facultyUploadTnpMessage",
    pyq: "facultyUploadPyqMessage",
    timetable: "facultyUploadTimetableMessage"
};

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
                <button type="button"
                    data-faculty-open-course="${escapeHtml(String(course.id ?? ""))}"
                    class="mt-4 w-full inline-flex items-center justify-center gap-2
                           rounded-xl bg-slate-100 px-3 py-2.5 text-sm font-semibold
                           text-slate-700 transition hover:bg-slate-200">
                    <i class="fa-regular fa-folder-open"></i>
                    View content
                </button>
            </article>
        `;
    }).join("");
}

// Fill one "My courses" picker with the optional bundles.
function fillBundleSelect(select) {
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

// "My courses" pickers for the optional note / TNP bundles.
function populateBundleSelect() {
    fillBundleSelect($("noteBundleSelect"));
    fillBundleSelect($("facultyTnpBundle"));
}

// =====================================================
// Content collections
// (GET /api/faculty/notes | tnp-notes | pyqs | timetables)
// =====================================================

async function loadFacultyNotes() {
    const rows = await apiGet("/api/faculty/notes");

    facultyNotes = Array.isArray(rows) ? rows : [];

    renderActiveManageCategory();
}

// TNP resources (owner-scoped like the notes).
async function loadFacultyTnp() {
    const rows = await apiGet("/api/faculty/tnp-notes");

    facultyTnpItems = Array.isArray(rows) ? rows : [];

    renderActiveManageCategory();
}

// Previous year papers (owner-scoped like the notes).
async function loadFacultyPyqs() {
    const rows = await apiGet("/api/faculty/pyqs");

    facultyPyqItems = Array.isArray(rows) ? rows : [];

    renderActiveManageCategory();
}

// Exam timetables (owner-scoped like the notes).
async function loadFacultyTimetables() {
    const rows = await apiGet("/api/faculty/timetables");

    facultyTimetableItems = Array.isArray(rows) ? rows : [];

    renderActiveManageCategory();
}

// Reload whichever collection the success message is about.
function reloadFacultyContent(category) {
    switch (category) {
        case "tnp":
            return loadFacultyTnp();
        case "pyq":
            return loadFacultyPyqs();
        case "timetable":
            return loadFacultyTimetables();
        default:
            return loadFacultyNotes();
    }
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
// Manage Content — category routing
// =====================================================
//
// One list container (#facultyNotesList) serves all four
// categories; the pill row picks which renderer runs.
// =====================================================

function renderActiveManageCategory() {
    switch (activeManageCategory) {
        case "tnp":
            renderFacultyTnp();
            break;
        case "pyq":
            renderFacultyPyq();
            break;
        case "timetable":
            renderFacultyTimetable();
            break;
        default:
            // Notes keep their original card rendering.
            renderFacultyNotes();
    }
}

// Resolve an id inside the collection for a category.
function findContentItem(category, itemId) {
    if (category === "notes") {
        return findNote(itemId);
    }

    const wanted = String(itemId);

    const source = {
        tnp: facultyTnpItems,
        pyq: facultyPyqItems,
        timetable: facultyTimetableItems
    }[category] || [];

    return source.find(
        item => String(item.id) === wanted
    ) || null;
}

// =====================================================
// Shared card markup for tnp / pyq / timetable
// (mirrors renderFacultyNotes so the look matches)
// =====================================================

function contentCardHtml(item, options) {
    const titleFallback = options.titleFallback || "Untitled content";
    const title = item.title || titleFallback;
    const metaLine = String(options.metaLine || "").trim();

    const fileUrl = typeof item.fileUrl === "string"
        ? item.fileUrl.trim()
        : "";

    // View only exists when there is a stored file.
    const canView = /^https?:\/\//i.test(fileUrl);

    const viewHtml = canView ? `
                    <a href="${escapeHtml(fileUrl)}"
                       target="_blank"
                       rel="noopener noreferrer"
                       class="inline-flex items-center gap-2 rounded-lg bg-blue-50
                              px-3 py-2 text-sm font-medium text-blue-700 transition
                              hover:bg-blue-100">
                        <i class="fa-solid fa-arrow-up-right-from-square"></i>
                        View PDF
                    </a>` : "";

    const metaHtml = metaLine ? `
                        <p class="mt-1 text-xs text-slate-400">
                            ${escapeHtml(metaLine)}
                        </p>` : "";

    const descriptionHtml = item.description ? `
                <p class="mt-3 text-sm text-slate-500 line-clamp-2">
                    ${escapeHtml(item.description)}
                </p>` : "";

    const bundleBadge = item.courseTitle ? `
                <span class="mt-3 inline-flex rounded-full bg-violet-50 px-2.5 py-1
                             text-xs font-bold text-violet-700">
                    ${escapeHtml(item.courseTitle)}
                </span>` : "";

    return `
            <article data-faculty-content-card
                class="resource-card rounded-2xl border border-gray-200
                       bg-white p-5 shadow-sm">
                <div class="flex items-start gap-3">
                    <div class="flex h-11 w-11 shrink-0 items-center justify-center
                                rounded-xl bg-blue-50 text-blue-600">
                        <i class="${escapeHtml(options.iconClass)}"></i>
                    </div>
                    <div class="min-w-0">
                        <h3 class="truncate text-base font-semibold text-gray-900"
                            title="${escapeHtml(title)}">
                            ${escapeHtml(title)}
                        </h3>
                        ${metaHtml}
                        <p class="mt-1 text-xs text-slate-400">
                            Uploaded ${escapeHtml(formatDate(item.createdAt))}
                        </p>
                    </div>
                </div>
                ${descriptionHtml}
                ${bundleBadge}
                <div class="mt-4 flex flex-wrap items-center gap-2">
                    ${viewHtml}
                    <button type="button"
                        class="faculty-edit-btn inline-flex items-center gap-2
                               rounded-lg bg-slate-100 px-3 py-2 text-sm font-medium
                               text-slate-700 transition hover:bg-slate-200"
                        data-note-id="${escapeHtml(String(item.id ?? ""))}">
                        <i class="fa-solid fa-pen"></i>
                        Edit
                    </button>
                </div>
            </article>
        `;
}

// =====================================================
// TNP (GET /api/faculty/tnp-notes)
// =====================================================

function tnpMetaLine(item) {
    // "topic" carries the subject / topic area label.
    return [item.topic || ""].filter(Boolean).join(" · ");
}

function renderFacultyTnp() {
    const list = $("facultyNotesList");

    if (!list) {
        return;
    }

    if (!facultyTnpItems.length) {
        list.innerHTML = emptyStateHtml(
            "fa-briefcase",
            "You haven't uploaded any TNP resources yet."
        );
        return;
    }

    list.innerHTML = facultyTnpItems.map(item => contentCardHtml(item, {
        iconClass: "fa-solid fa-briefcase",
        titleFallback: "Untitled TNP resource",
        metaLine: tnpMetaLine(item)
    })).join("");
}

// =====================================================
// PYQ (GET /api/faculty/pyqs)
// =====================================================

function pyqMetaLine(item) {
    const parts = [
        item.subject || "",
        item.course || "",
        item.branch || ""
    ].filter(Boolean);

    if (Number(item.semester) > 0) {
        parts.push(`Semester ${Number(item.semester)}`);
    }

    if (Number(item.examYear) > 0) {
        parts.push(`${Number(item.examYear)}`);
    }

    return parts.join(" · ");
}

function renderFacultyPyq() {
    const list = $("facultyNotesList");

    if (!list) {
        return;
    }

    if (!facultyPyqItems.length) {
        list.innerHTML = emptyStateHtml(
            "fa-clock-rotate-left",
            "You haven't uploaded any previous year papers yet."
        );
        return;
    }

    list.innerHTML = facultyPyqItems.map(item => contentCardHtml(item, {
        iconClass: "fa-solid fa-clock-rotate-left",
        titleFallback: "Untitled previous year paper",
        metaLine: pyqMetaLine(item)
    })).join("");
}

// =====================================================
// Timetable (GET /api/faculty/timetables)
// =====================================================

function timetableMetaLine(item) {
    const parts = [
        item.course || "",
        item.examType || ""
    ].filter(Boolean);

    if (Number(item.semester) > 0) {
        parts.push(`Semester ${Number(item.semester)}`);
    }

    if (Number(item.examYear) > 0) {
        parts.push(`${Number(item.examYear)}`);
    }

    return parts.join(" · ");
}

function renderFacultyTimetable() {
    const list = $("facultyNotesList");

    if (!list) {
        return;
    }

    if (!facultyTimetableItems.length) {
        list.innerHTML = emptyStateHtml(
            "fa-calendar-days",
            "You haven't uploaded any exam timetables yet."
        );
        return;
    }

    list.innerHTML = facultyTimetableItems.map(item => contentCardHtml(item, {
        iconClass: "fa-solid fa-calendar-days",
        titleFallback: "Untitled timetable",
        metaLine: timetableMetaLine(item)
    })).join("");
}

// =====================================================
// Edit modal (own markup in faculty.html)
// =====================================================
//
// One modal serves all four categories: the hidden
// #facultyEditCategory field remembers which collection the
// record came from, drives the visible field groups and
// decides where handleEditSubmit PUTs the update.
// =====================================================

// Field-group wrapper ids inside #facultyEditForm.
const EDIT_GROUP_IDS = {
    subject: "facultyEditGroupSubject",
    title: "facultyEditGroupTitle",
    course: "facultyEditGroupCourse",
    branch: "facultyEditGroupBranch",
    semester: "facultyEditGroupSemester",
    examYear: "facultyEditGroupExamYear",
    examType: "facultyEditGroupExamType",
    description: "facultyEditGroupDescription",
    file: "facultyEditGroupFile"
};

// Which field groups each category exposes. Timetables carry
// no description; notes/tnp/pyq share subject/title/
// description/replace-file.
const EDIT_FIELDS_BY_CATEGORY = {
    notes: ["subject", "title", "description", "file"],
    tnp: ["subject", "title", "description", "file"],
    pyq: [
        "subject", "title", "course", "branch",
        "semester", "examYear", "description", "file"
    ],
    timetable: ["course", "semester", "examYear", "examType", "title", "file"]
};

// One PUT route per content category (all owner-scoped).
const EDIT_ENDPOINTS = {
    notes: id => `/api/faculty/notes/${encodeURIComponent(id)}`,
    tnp: id => `/api/faculty/tnp-notes/${encodeURIComponent(id)}`,
    pyq: id => `/api/faculty/pyqs/${encodeURIComponent(id)}`,
    timetable: id => `/api/faculty/timetables/${encodeURIComponent(id)}`
};

function openEditModal(category, item) {
    const modal = $("facultyEditModal");

    if (!modal || !item) {
        return;
    }

    const activeCategory = EDIT_FIELDS_BY_CATEGORY[category]
        ? category
        : "notes";

    const fields = EDIT_FIELDS_BY_CATEGORY[activeCategory];

    // Show only the field groups this category uses.
    Object.entries(EDIT_GROUP_IDS).forEach(([field, elementId]) => {
        $(elementId)?.classList.toggle(
            "hidden",
            !fields.includes(field)
        );
    });

    const idField = $("facultyEditNoteId");
    const categoryField = $("facultyEditCategory");

    if (idField) {
        idField.value = String(item.id ?? "");
    }

    if (categoryField) {
        categoryField.value = activeCategory;
    }

    // Heading + subject wording follow the category.
    const heading = $("facultyEditHeading");

    if (heading) {
        heading.textContent = `Edit ${CATEGORY_LABELS[activeCategory]}`;
    }

    const subjectLabel = $("facultyEditSubjectLabel");

    if (subjectLabel) {
        // TNP stores its subject / topic area under "topic".
        subjectLabel.textContent = activeCategory === "tnp"
            ? "Subject / Topic"
            : "Subject";
    }

    const subjectField = $("facultyEditSubject");

    if (subjectField) {
        subjectField.value = activeCategory === "tnp"
            ? (item.topic || "")
            : (item.subject || "");
    }

    const titleField = $("facultyEditTitle");

    if (titleField) {
        titleField.value = item.title || "";
    }

    const courseField = $("facultyEditCourse");

    if (courseField) {
        courseField.value = item.course || "";
    }

    const branchField = $("facultyEditBranch");

    if (branchField) {
        branchField.value = item.branch || "";
    }

    const semesterField = $("facultyEditSemester");

    if (semesterField) {
        // 0 / absent means "no semester selected".
        semesterField.value = Number(item.semester) > 0
            ? String(Number(item.semester))
            : "";
    }

    const examYearField = $("facultyEditExamYear");

    if (examYearField) {
        examYearField.value = Number(item.examYear) > 0
            ? String(Number(item.examYear))
            : "";
    }

    const examTypeField = $("facultyEditExamType");

    if (examTypeField) {
        examTypeField.value = item.examType || "";
    }

    const descriptionField = $("facultyEditDescription");

    if (descriptionField) {
        descriptionField.value = item.description || "";
    }

    const fileField = $("facultyEditFile");

    if (fileField) {
        fileField.value = "";
    }

    facultyMessage("facultyEditMessage", "");

    modal.classList.remove("hidden");
    modal.classList.add("flex");

    // Focus the first visible field of this category.
    const firstVisibleField = Array.from(
        modal.querySelectorAll(
            "input:not([type='hidden']), select, textarea"
        )
    ).find(field => !field.closest(".hidden"));

    firstVisibleField?.focus();
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
// Course content viewer (read-only modal)
// GET /api/faculty/courses/{id}/content
// =====================================================
//
// The endpoint answers { course, notes, tnpNotes, pyqs,
// timetables } using the exact same item shapes as the four
// owner-scoped collections, so the existing per-category meta
// helpers describe every row.
//
// This viewer is read-only by design: there is NO edit or
// delete affordance here. Editing stays in #facultyEditModal
// and removal stays behind the admin endpoints only.
// =====================================================

// One block per content collection inside the modal body.
const COURSE_CONTENT_GROUPS = [
    {
        heading: "Notes",
        iconClass: "fa-solid fa-file-lines",
        titleFallback: "Untitled note",
        itemsKey: "notes",
        metaLine: noteMetaLine
    },
    {
        heading: "TNP Resources",
        iconClass: "fa-solid fa-briefcase",
        titleFallback: "Untitled TNP resource",
        itemsKey: "tnpNotes",
        metaLine: tnpMetaLine
    },
    {
        heading: "Previous Year Papers",
        iconClass: "fa-solid fa-clock-rotate-left",
        titleFallback: "Untitled previous year paper",
        itemsKey: "pyqs",
        metaLine: pyqMetaLine
    },
    {
        heading: "Exam Timetables",
        iconClass: "fa-solid fa-calendar-days",
        titleFallback: "Untitled timetable",
        itemsKey: "timetables",
        metaLine: timetableMetaLine
    }
];

// Read-only sibling of contentCardHtml(): same card look,
// but only the View PDF link - never Edit / delete buttons.
function courseContentCardHtml(item, options) {
    const title = item.title || options.titleFallback;
    const metaLine = String(options.metaLine(item) || "").trim();

    // Rows normally carry camelCase fileUrl; file_url is kept
    // as a fallback in case a payload arrives snake-cased.
    const fileUrl = typeof item.fileUrl === "string"
        ? item.fileUrl.trim()
        : (typeof item.file_url === "string"
            ? item.file_url.trim()
            : "");

    const canView = /^https?:\/\//i.test(fileUrl);

    const viewHtml = canView ? `
                <div class="mt-3">
                    <a href="${escapeHtml(fileUrl)}"
                       target="_blank"
                       rel="noopener noreferrer"
                       class="inline-flex items-center gap-2 rounded-lg bg-blue-50
                              px-3 py-2 text-sm font-medium text-blue-700 transition
                              hover:bg-blue-100">
                        <i class="fa-solid fa-arrow-up-right-from-square"></i>
                        View PDF
                    </a>
                </div>` : "";

    const metaHtml = metaLine ? `
                        <p class="mt-1 text-xs text-slate-400">
                            ${escapeHtml(metaLine)}
                        </p>` : "";

    return `
            <article class="resource-card rounded-2xl border border-gray-200
                           bg-white p-4 shadow-sm">
                <div class="flex items-start gap-3">
                    <div class="flex h-10 w-10 shrink-0 items-center justify-center
                                rounded-xl bg-blue-50 text-blue-600">
                        <i class="${escapeHtml(options.iconClass)}"></i>
                    </div>
                    <div class="min-w-0">
                        <h4 class="truncate text-sm font-semibold text-gray-900"
                            title="${escapeHtml(title)}">
                            ${escapeHtml(title)}
                        </h4>
                        ${metaHtml}
                        <p class="mt-1 text-xs text-slate-400">
                            Uploaded ${escapeHtml(formatDate(item.createdAt))}
                        </p>
                    </div>
                </div>
                ${viewHtml}
            </article>
        `;
}

// One labelled block (subheading + item grid) per category.
function courseContentGroupHtml(group, items) {
    return `
        <section>
            <div class="mb-3 flex items-center gap-2">
                <i class="${escapeHtml(group.iconClass)} text-slate-400 text-sm"></i>
                <h4 class="text-sm font-extrabold uppercase tracking-wide text-slate-700">
                    ${escapeHtml(group.heading)}
                </h4>
                <span class="rounded-full bg-slate-100 px-2 py-0.5
                             text-xs font-bold text-slate-500">
                    ${items.length}
                </span>
            </div>
            <div class="grid sm:grid-cols-2 gap-4">
                ${items.map(item => courseContentCardHtml(item, group)).join("")}
            </div>
        </section>
    `;
}

// Fill #facultyCourseContentBody with whichever categories
// actually hold content (empty course -> shared empty state).
function renderCourseContentBody(data) {
    const body = $("facultyCourseContentBody");

    if (!body) {
        return;
    }

    const blocks = COURSE_CONTENT_GROUPS
        .map(group => ({
            group,
            items: Array.isArray(data?.[group.itemsKey])
                ? data[group.itemsKey]
                : []
        }))
        .filter(entry => entry.items.length);

    if (!blocks.length) {
        body.innerHTML = emptyStateHtml(
            "fa-folder-open",
            "No content uploaded for this course yet."
        );
        return;
    }

    body.innerHTML = blocks
        .map(entry => courseContentGroupHtml(entry.group, entry.items))
        .join("");
}

async function openCourseContent(courseId) {
    const modal = $("facultyCourseContentModal");
    const title = $("facultyCourseContentTitle");
    const body = $("facultyCourseContentBody");

    if (!modal || !body) {
        return;
    }

    if (title) {
        title.textContent = "Course Content";
    }

    body.innerHTML = `
        <div class="rounded-2xl border border-dashed
                    border-gray-300 bg-white p-10
                    text-center shadow-sm">
            <div class="mx-auto mb-4 flex h-14 w-14
                        items-center justify-center
                        rounded-full bg-gray-100">
                <i class="fa-solid fa-spinner fa-spin text-slate-400 text-xl"></i>
            </div>
            <p class="text-sm text-slate-500">Loading course content…</p>
        </div>
    `;

    modal.classList.remove("hidden");
    modal.classList.add("flex");

    try {
        const data = await apiGet(
            `/api/faculty/courses/${encodeURIComponent(String(courseId))}/content`
        );

        if (title) {
            title.textContent = data?.course?.title || "Course Content";
        }

        renderCourseContentBody(data);
    } catch (error) {
        console.error("Faculty course content loading error:", error);

        // 403 / 404 carry the backend's message through apiGet().
        body.innerHTML = `
            <div class="rounded-2xl border border-red-200
                        bg-red-50 px-4 py-3 text-sm text-red-700">
                ${escapeHtml(errorMessage(
                    error,
                    "Could not load course content."
                ))}
            </div>
        `;
    }
}

function closeCourseContentModal() {
    const modal = $("facultyCourseContentModal");

    if (!modal) {
        return;
    }

    modal.classList.add("hidden");
    modal.classList.remove("flex");

    const body = $("facultyCourseContentBody");

    if (body) {
        body.innerHTML = "";
    }
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
// TNP upload — uploadPdf() then POST /api/faculty/tnp-notes
// =====================================================

async function handleUploadTnp(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const button = $("facultyUploadTnpBtn");
    const messageElement = UPLOAD_MESSAGE_IDS.tnp;

    const topic = getValue("facultyTnpTopic");
    const file = getFile("facultyTnpFile");

    if (!topic) {
        facultyMessage(
            messageElement,
            "Please enter the subject / topic area.",
            "error"
        );
        return;
    }

    if (!file) {
        facultyMessage(
            messageElement,
            "Please select a PDF file.",
            "error"
        );
        return;
    }

    if (button) {
        button.disabled = true;
    }

    facultyMessage(
        messageElement,
        "Uploading PDF to Cloudinary…",
        "success",
        0
    );

    try {
        // Browser -> Cloudinary (same pipeline as admin).
        const uploaded = await uploadPdf(file, CONTENT_FOLDERS.tnp);

        const fileUrl = uploaded?.file_url || null;
        const storagePath = uploaded?.cloudinary_public_id || null;

        if (!fileUrl) {
            throw new Error(
                "Upload succeeded, but no file URL was returned."
            );
        }

        const payload = {
            title: getValue("facultyTnpTitle") || null,
            // The "Subject / Topic area" input maps to topic.
            topic,
            fileUrl,
            storagePath,
            // Optional bundle this TNP resource unlocks.
            courseId: getNumber("facultyTnpBundle")
        };

        await apiPost("/api/faculty/tnp-notes", payload);

        form?.reset();

        facultyMessage(
            messageElement,
            "TNP resource uploaded successfully."
        );

        await loadFacultyTnp();
        dispatchRefresh();
    } catch (error) {
        console.error("Faculty TNP upload error:", error);

        facultyMessage(
            messageElement,
            errorMessage(error, "Could not upload the TNP resource."),
            "error"
        );
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

// =====================================================
// PYQ upload — uploadPdf() then POST /api/faculty/pyqs
// =====================================================

async function handleUploadPyq(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const button = $("facultyUploadPyqBtn");
    const messageElement = UPLOAD_MESSAGE_IDS.pyq;

    const subject = getValue("facultyPyqSubject");
    const file = getFile("facultyPyqFile");

    if (!subject) {
        facultyMessage(
            messageElement,
            "Please enter the subject.",
            "error"
        );
        return;
    }

    if (!file) {
        facultyMessage(
            messageElement,
            "Please select a PDF file.",
            "error"
        );
        return;
    }

    if (button) {
        button.disabled = true;
    }

    facultyMessage(
        messageElement,
        "Uploading PDF to Cloudinary…",
        "success",
        0
    );

    try {
        // Browser -> Cloudinary (same pipeline as admin).
        const uploaded = await uploadPdf(file, CONTENT_FOLDERS.pyq);

        const fileUrl = uploaded?.file_url || null;
        const storagePath = uploaded?.cloudinary_public_id || null;

        if (!fileUrl) {
            throw new Error(
                "Upload succeeded, but no file URL was returned."
            );
        }

        const payload = {
            title: getValue("facultyPyqTitle") || null,
            subject,
            course: getValue("facultyPyqCourse") || null,
            branch: getValue("facultyPyqBranch") || null,
            // 0 = no semester selected (PyqResponse contract).
            semester: getNumber("facultyPyqSemester") || 0,
            examYear: getNumber("facultyPyqExamYear"),
            fileUrl,
            storagePath
        };

        await apiPost("/api/faculty/pyqs", payload);

        form?.reset();

        facultyMessage(
            messageElement,
            "Previous year paper uploaded successfully."
        );

        await loadFacultyPyqs();
        dispatchRefresh();
    } catch (error) {
        console.error("Faculty PYQ upload error:", error);

        facultyMessage(
            messageElement,
            errorMessage(error, "Could not upload the previous year paper."),
            "error"
        );
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

// =====================================================
// Timetable upload — uploadPdf() then POST /api/faculty/timetables
// =====================================================

async function handleUploadTimetable(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const button = $("facultyUploadTimetableBtn");
    const messageElement = UPLOAD_MESSAGE_IDS.timetable;

    const course = getValue("facultyTimetableCourse");
    const examType = getValue("facultyTimetableExamType");
    const file = getFile("facultyTimetableFile");

    if (!course) {
        facultyMessage(
            messageElement,
            "Please enter the course.",
            "error"
        );
        return;
    }

    if (!examType) {
        facultyMessage(
            messageElement,
            "Please select the exam type.",
            "error"
        );
        return;
    }

    if (!file) {
        facultyMessage(
            messageElement,
            "Please select a timetable file.",
            "error"
        );
        return;
    }

    if (button) {
        button.disabled = true;
    }

    facultyMessage(
        messageElement,
        "Uploading file to Cloudinary…",
        "success",
        0
    );

    try {
        // Browser -> Cloudinary (same pipeline as admin).
        const uploaded = await uploadPdf(file, CONTENT_FOLDERS.timetable);

        const fileUrl = uploaded?.file_url || null;
        const storagePath = uploaded?.cloudinary_public_id || null;

        if (!fileUrl) {
            throw new Error(
                "Upload succeeded, but no file URL was returned."
            );
        }

        // No title here: the backend composes it from course +
        // examType + examYear when the title is omitted.
        const payload = {
            course,
            semester: getNumber("facultyTimetableSemester"),
            examYear: getNumber("facultyTimetableExamYear"),
            examType,
            fileUrl,
            storagePath,
            // Original file name (the admin flow stores it too).
            fileName: file.name
        };

        await apiPost("/api/faculty/timetables", payload);

        form?.reset();

        facultyMessage(
            messageElement,
            "Exam timetable uploaded successfully."
        );

        await loadFacultyTimetables();
        dispatchRefresh();
    } catch (error) {
        console.error("Faculty timetable upload error:", error);

        facultyMessage(
            messageElement,
            errorMessage(error, "Could not upload the exam timetable."),
            "error"
        );
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

// =====================================================
// Upload category selector — swaps the four upload forms
// =====================================================
//
// Each category lives in its own panel (message + form), so
// a late message from a hidden form can never surface under
// the category the user is looking at.
// =====================================================

function updateFacultyUploadCategoryPanel() {
    const category = $("facultyUploadCategory")?.value || "notes";

    const panels = {
        notes: $("facultyUploadNotesPanel"),
        tnp: $("facultyUploadTnpPanel"),
        pyq: $("facultyUploadPyqPanel"),
        timetable: $("facultyUploadTimetablePanel")
    };

    Object.entries(panels).forEach(([name, panel]) => {
        if (!panel) {
            return;
        }

        panel.classList.toggle("hidden", name !== category);
    });

    // Subtitle follows the selected category.
    const subtitle = $("facultyUploadSubtitle");

    if (subtitle) {
        subtitle.textContent = UPLOAD_SUBTITLES[category]
            || UPLOAD_SUBTITLES.notes;
    }
}

// =====================================================
// Edit content — optional uploadPdf() then
// PUT /api/faculty/notes/{id} | tnp-notes/{id} |
//                         pyqs/{id} | timetables/{id}
// =====================================================

// Only the fields of the active category are sent; blank
// optional fields become null so the backend keeps the
// current values (partial PUT in the services).
function buildEditPayload(category) {
    switch (category) {
        case "tnp":
            return {
                // The shared Subject input maps to "topic".
                topic: getValue("facultyEditSubject") || null,
                title: getValue("facultyEditTitle") || null,
                description: getValue("facultyEditDescription") || null
            };

        case "pyq":
            return {
                subject: getValue("facultyEditSubject") || null,
                title: getValue("facultyEditTitle") || null,
                course: getValue("facultyEditCourse") || null,
                branch: getValue("facultyEditBranch") || null,
                semester: getNumber("facultyEditSemester"),
                examYear: getNumber("facultyEditExamYear"),
                description: getValue("facultyEditDescription") || null
            };

        case "timetable":
            return {
                // Timetable records carry no description field.
                title: getValue("facultyEditTitle") || null,
                course: getValue("facultyEditCourse") || null,
                semester: getNumber("facultyEditSemester"),
                examYear: getNumber("facultyEditExamYear"),
                examType: getValue("facultyEditExamType") || null
            };

        default:
            return {
                title: getValue("facultyEditTitle") || null,
                subject: getValue("facultyEditSubject") || null,
                description: getValue("facultyEditDescription") || null
            };
    }
}

async function handleEditSubmit(event) {
    event.preventDefault();

    const contentId = $("facultyEditNoteId")?.value;

    if (!contentId) {
        return;
    }

    const category = $("facultyEditCategory")?.value || "notes";
    const label = CATEGORY_LABELS[category] || CATEGORY_LABELS.notes;
    const endpointBuilder = EDIT_ENDPOINTS[category] || EDIT_ENDPOINTS.notes;

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
        const payload = buildEditPayload(category);

        if (file) {
            const uploaded = await uploadPdf(
                file,
                CONTENT_FOLDERS[category] || PDF_FOLDER
            );

            const fileUrl = uploaded?.file_url || null;

            if (!fileUrl) {
                throw new Error(
                    "Upload succeeded, but no file URL was returned."
                );
            }

            payload.fileUrl = fileUrl;
            payload.storagePath =
                uploaded?.cloudinary_public_id || "";

            // Timetable records also keep the original file name.
            if (category === "timetable") {
                payload.fileName = file.name;
            }
        }

        await apiPut(endpointBuilder(contentId), payload);

        closeEditModal();

        facultyMessage(
            "facultyManageMessage",
            `${label} updated successfully.`
        );

        await reloadFacultyContent(category);
        dispatchRefresh();
    } catch (error) {
        console.error(`Faculty ${label} edit error:`, error);

        facultyMessage(
            "facultyEditMessage",
            errorMessage(
                error,
                `Could not update the ${label.toLowerCase()}.`
            ),
            "error"
        );
    } finally {
        if (button) {
            button.disabled = false;
        }
    }
}

// =====================================================
// Sidebar-hash scrolling
// =====================================================
//
// The shared sidebar links to ./faculty.html#course | #upload |
// #launch | #manage - short hashes that match no element id, so
// the browser's native anchor jump is a no-op. Map each hash to
// the real section id and scroll by hand instead.
// =====================================================

const FACULTY_SECTION_HASHES = {
    course: "facultyCourseSection",
    launch: "facultyLaunchSection",
    upload: "facultyUploadSection",
    manage: "facultyManageSection"
};

function scrollToFacultyHash() {
    const hash = String(window.location.hash || "").replace(/^#/, "");

    if (!hash) {
        return;
    }

    const sectionId = FACULTY_SECTION_HASHES[hash];

    if (!sectionId) {
        return;
    }

    // "instant" (not "smooth"): the stylesheet sets a global
    // scroll-behavior:smooth, and layering a JS-smooth scroll on top
    // of it makes long jumps stall and land late.
    $(sectionId)?.scrollIntoView({
        behavior: "instant",
        block: "start"
    });
}

// =====================================================
// Events (wired once, null-guarded like other modules)
// =====================================================

// Manage Content pill classes (active = blue bg, white text).
const MANAGE_PILL_ACTIVE_CLASSES = [
    "bg-blue-600",
    "text-white",
    "border-blue-600"
];

const MANAGE_PILL_INACTIVE_CLASSES = [
    "bg-white",
    "text-slate-600",
    "border-slate-200",
    "hover:bg-slate-50"
];

function updateManageCategoryPills() {
    const pills = $("facultyManageSection")?.querySelectorAll(
        "[data-faculty-manage-category]"
    ) || [];

    pills.forEach(pill => {
        const isActive = pill.dataset.facultyManageCategory
            === activeManageCategory;

        MANAGE_PILL_ACTIVE_CLASSES.forEach(
            className => pill.classList.toggle(className, isActive)
        );

        MANAGE_PILL_INACTIVE_CLASSES.forEach(
            className => pill.classList.toggle(className, !isActive)
        );
    });
}

function handleManageCategoryClick(event) {
    const pill = event.target.closest(
        "[data-faculty-manage-category]"
    );

    if (!pill) {
        return;
    }

    const category = pill.dataset.facultyManageCategory;

    if (!category) {
        return;
    }

    activeManageCategory = category;

    updateManageCategoryPills();

    // Errors from the category we just left must not linger.
    facultyMessage("facultyManageMessage", "");

    renderActiveManageCategory();
}

// Edit buttons live inside every card flavour rendered into
// #facultyNotesList — resolve the id against the active
// category and open the shared modal.
function handleContentListClick(event) {
    const button = event.target.closest(".faculty-edit-btn");

    if (!button) {
        return;
    }

    const item = findContentItem(
        activeManageCategory,
        button.dataset.noteId
    );

    if (!item) {
        return;
    }

    openEditModal(activeManageCategory, item);
}

// "View content" buttons live in the course cards rendered
// into #facultyCoursesList (My Courses section) and open the
// read-only viewer for that course.
function handleCourseListClick(event) {
    const button = event.target.closest(
        "[data-faculty-open-course]"
    );

    if (!button) {
        return;
    }

    const courseId = button.dataset.facultyOpenCourse;

    if (!courseId) {
        return;
    }

    openCourseContent(courseId);
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

    // ---- Upload (one form per category) ----
    $("uploadNoteForm")?.addEventListener(
        "submit",
        handleUploadNote
    );

    $("facultyUploadCategory")?.addEventListener(
        "change",
        updateFacultyUploadCategoryPanel
    );

    $("facultyUploadTnpForm")?.addEventListener(
        "submit",
        handleUploadTnp
    );

    $("facultyUploadPyqForm")?.addEventListener(
        "submit",
        handleUploadPyq
    );

    $("facultyUploadTimetableForm")?.addEventListener(
        "submit",
        handleUploadTimetable
    );

    // ---- Manage Content (category pills) ----
    $("facultyManageSection")?.addEventListener(
        "click",
        handleManageCategoryClick
    );

    // ---- Edit modal (one modal, four categories) ----
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
        handleContentListClick
    );

    // ---- Course content viewer (read-only modal) ----
    $("facultyCoursesList")?.addEventListener(
        "click",
        handleCourseListClick
    );

    $("facultyCourseContentClose")?.addEventListener(
        "click",
        closeCourseContentModal
    );

    // Click on the dimmed backdrop closes the viewer.
    $("facultyCourseContentModal")?.addEventListener(
        "click",
        event => {
            if (event.target === event.currentTarget) {
                closeCourseContentModal();
            }
        }
    );

    // Sidebar hashes (#course, #launch, …) never match an
    // element id, so the native anchor jump never fires while
    // already on faculty.html - scroll to the section by hand.
    window.addEventListener(
        "hashchange",
        scrollToFacultyHash
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

        // The viewer sits above the edit modal, so it gets
        // first refusal on the Escape key.
        const contentModal = $("facultyCourseContentModal");

        if (contentModal && !contentModal.classList.contains("hidden")) {
            closeCourseContentModal();
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

    // Sync the upload panels with the category selector
    // (the markup already defaults to notes).
    updateFacultyUploadCategoryPanel();

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
            "facultyCourseMessage",
            errorMessage(error, "Could not load your courses."),
            "error"
        );
    }

    // All four content collections load up front so the
    // Manage Content pills switch instantly; each one is
    // guarded on its own so one bad endpoint cannot hide
    // the rest of the page.
    const contentLoaders = [
        { label: "notes", load: loadFacultyNotes },
        { label: "TNP resources", load: loadFacultyTnp },
        { label: "previous year papers", load: loadFacultyPyqs },
        { label: "exam timetables", load: loadFacultyTimetables }
    ];

    await Promise.all(contentLoaders.map(async ({ label, load }) => {
        try {
            await load();
        } catch (error) {
            console.error(`Faculty ${label} loading error:`, error);

            facultyMessage(
                "facultyManageMessage",
                errorMessage(error, `Could not load your ${label}.`),
                "error"
            );
        }
    }));

    // Deep link from the sidebar (./faculty.html#course …):
    // wait until the sections exist so the jump lands correctly.
    scrollToFacultyHash();
}
