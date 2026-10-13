// =====================================================
// TechCampus_Hub
// certificates.js
//
// Certification section: students open admin-managed
// certificate links; admins add, edit and delete them.
//
// Storage: Supabase `certificates` table created by
// sql/setup.sql (Part 2). When the table is missing the
// module falls back to this-browser-only storage so the
// feature still works before the migration is run.
// =====================================================

import { supabase } from "../supabase.js";
import { $, escapeHtml } from "./helpers.js";
import { currentUserRole } from "./state.js";

// =====================================================
// Constants
// =====================================================

const LOCAL_KEY = "campusnotes:certificates";

// =====================================================
// Module state
// =====================================================

let certificatesCache = [];
let certificatesLoaded = false;
let eventsWired = false;

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

// =====================================================
// Storage
// =====================================================

export async function loadCertificates(force = false) {
    if (certificatesLoaded && !force) {
        return certificatesCache;
    }

    certificatesLoaded = true;
    certificatesCache = [];

    const isAdmin = currentUserRole === "admin";

    let query = supabase
        .from("certificates")
        .select("*")
        .order("sort_order", { ascending: true })
        .order("created_at", { ascending: true });

    if (!isAdmin) {
        query = query.eq("is_active", true);
    }

    const {
        data,
        error
    } = await query;

    if (error) {
        if (!isMissingTable(error)) {
            console.error("Certificates loading error:", error);
        }

        certificatesCache = localRead(LOCAL_KEY, []).filter(
            row => isAdmin || row.is_active !== false
        );

        return certificatesCache;
    }

    certificatesCache = data || [];

    return certificatesCache;
}

export async function createCertificate(fields) {
    const title = String(fields.title || "").trim();
    const url = String(fields.url || "").trim();

    if (!title) {
        throw new Error("Certificate title is required.");
    }

    if (!/^https?:\/\//i.test(url)) {
        throw new Error("Certificate link must start with http:// or https://");
    }

    const row = {
        title,
        url,
        description: String(fields.description || "").trim(),
        sort_order: Number(fields.sort_order) || 0,
        is_active: fields.is_active !== false
    };

    const {
        data,
        error
    } = await supabase
        .from("certificates")
        .insert(row)
        .select()
        .single();

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const localRow = {
            ...row,
            id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            created_at: new Date().toISOString()
        };

        const all = localRead(LOCAL_KEY, []);
        all.push(localRow);
        localWrite(LOCAL_KEY, all);

        certificatesCache.push(localRow);

        return { row: localRow, mode: "local" };
    }

    certificatesCache.push(data);

    return { row: data, mode: "cloud" };
}

export async function updateCertificate(certificateId, fields) {
    const patch = {};

    if (fields.title !== undefined) {
        const title = String(fields.title || "").trim();

        if (!title) {
            throw new Error("Certificate title is required.");
        }

        patch.title = title;
    }

    if (fields.url !== undefined) {
        const url = String(fields.url || "").trim();

        if (!/^https?:\/\//i.test(url)) {
            throw new Error(
                "Certificate link must start with http:// or https://"
            );
        }

        patch.url = url;
    }

    if (fields.description !== undefined) {
        patch.description = String(fields.description || "").trim();
    }

    if (fields.is_active !== undefined) {
        patch.is_active = Boolean(fields.is_active);
    }

    const {
        error
    } = await supabase
        .from("certificates")
        .update(patch)
        .eq("id", certificateId);

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const all = localRead(LOCAL_KEY, []);

        const index = all.findIndex(
            row => String(row.id) === String(certificateId)
        );

        if (index === -1) {
            throw new Error("Certificate not found.");
        }

        all[index] = { ...all[index], ...patch };
        localWrite(LOCAL_KEY, all);

        return { mode: "local" };
    }

    return { mode: "cloud" };
}

export async function deleteCertificate(certificateId) {
    const {
        error
    } = await supabase
        .from("certificates")
        .delete()
        .eq("id", certificateId);

    if (error) {
        if (!isMissingTable(error)) {
            throw error;
        }

        const all = localRead(LOCAL_KEY, []);

        localWrite(
            LOCAL_KEY,
            all.filter(row => String(row.id) !== String(certificateId))
        );
    }

    certificatesCache = certificatesCache.filter(
        row => String(row.id) !== String(certificateId)
    );
}

// =====================================================
// Student page (certificates.html)
// =====================================================

export async function renderCertificatesPage() {
    const list = $("certificatesList");
    const empty = $("certificatesEmptyState");

    if (!list) {
        return;
    }

    const rows = await loadCertificates(true);

    if (empty) {
        empty.classList.toggle("hidden", rows.length > 0);
    }

    list.querySelectorAll("[data-certificate-card]").forEach(
        node => node.remove()
    );

    if (!rows.length) {
        return;
    }

    const html = rows.map(row => `
        <article data-certificate-card
            class="resource-card rounded-2xl border border-gray-200
                   bg-white p-5 shadow-sm">
            <div class="flex items-start gap-3">
                <div class="flex h-11 w-11 shrink-0 items-center justify-center
                            rounded-xl bg-amber-50 text-amber-600">
                    <i class="fa-solid fa-certificate"></i>
                </div>
                <div class="min-w-0">
                    <h3 class="truncate text-base font-semibold text-gray-900"
                        title="${escapeHtml(row.title || "Certificate")}">
                        ${escapeHtml(row.title || "Certificate")}
                    </h3>
                    <p class="mt-1 text-sm text-slate-500">
                        ${escapeHtml(row.description || "Click below to get your certificate.")}
                    </p>
                </div>
            </div>
            <a href="${escapeHtml(row.url || "#")}"
               target="_blank"
               rel="noopener noreferrer"
               class="mt-4 inline-flex w-full items-center justify-center gap-2
                      rounded-xl bg-blue-600 hover:bg-blue-700 text-white
                      font-bold py-3 transition">
                <i class="fa-solid fa-arrow-up-right-from-square"></i>
                Get certificate
            </a>
        </article>
    `).join("");

    list.insertAdjacentHTML("beforeend", html);
}

// =====================================================
// Admin — manage certificate links (admin.html)
// =====================================================

function adminMessage(text, type = "info") {
    const box = $("certificateAdminMessage");

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

async function renderAdminCertificateList() {
    const list = $("adminCertificatesList");

    if (!list) {
        return;
    }

    const rows = await loadCertificates(true);

    if (!rows.length) {
        list.innerHTML = `
            <p class="text-sm text-slate-500">No certificate links yet.</p>
        `;
        return;
    }

    list.innerHTML = rows.map(row => `
        <div class="border rounded-xl p-4 flex flex-wrap items-center gap-4 justify-between">
            <div class="min-w-0">
                <p class="font-bold text-slate-800 truncate">
                    ${escapeHtml(row.title || "Certificate")}
                    ${row.is_active === false
                        ? `<span class="ml-2 rounded bg-slate-100 px-2 py-0.5
                             text-xs font-bold text-slate-500">Hidden</span>`
                        : ""}
                </p>
                <p class="text-sm text-slate-500 truncate">
                    ${escapeHtml(row.description || "")}
                </p>
                <a href="${escapeHtml(row.url || "#")}" target="_blank"
                   rel="noopener noreferrer"
                   class="text-xs text-blue-600 underline break-all">
                    ${escapeHtml(row.url || "")}
                </a>
            </div>
            <div class="flex gap-2">
                <button type="button"
                    class="edit-certificate-btn rounded-lg bg-blue-50 hover:bg-blue-100
                           text-blue-700 text-xs font-bold px-3 py-2"
                    data-certificate-id="${escapeHtml(String(row.id))}">
                    Edit
                </button>
                <button type="button"
                    class="delete-certificate-btn rounded-lg bg-red-50 hover:bg-red-100
                           text-red-600 text-xs font-bold px-3 py-2"
                    data-certificate-id="${escapeHtml(String(row.id))}">
                    Delete
                </button>
            </div>
        </div>
    `).join("");
}

function resetCertificateForm() {
    const form = $("certificateAdminForm");

    if (!form) {
        return;
    }

    delete form.dataset.editingId;
    form.reset();

    const saveBtn = $("saveCertBtn");

    if (saveBtn) {
        saveBtn.innerHTML = `<i class="fa-solid fa-plus mr-2"></i>Add certificate`;
    }

    $("cancelCertEditBtn")?.classList.add("hidden");
}

async function handleCertificateSubmit(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const editingId = form.dataset.editingId || null;

    const fields = {
        title: $("certTitleInput")?.value,
        url: $("certUrlInput")?.value,
        description: $("certDescriptionInput")?.value
    };

    try {
        if (editingId) {
            await updateCertificate(editingId, fields);
            adminMessage("Certificate updated.", "success");
        } else {
            const result = await createCertificate(fields);

            adminMessage(
                result.mode === "local"
                    ? "Certificate saved (local — run sql/setup.sql for database storage)."
                    : "Certificate added.",
                "success"
            );
        }

        resetCertificateForm();
        await renderAdminCertificateList();
    } catch (error) {
        console.error("Certificate save error:", error);

        adminMessage(
            error?.message || "Could not save the certificate.",
            "error"
        );
    }
}

async function handleCertificateListClick(event) {
    const editButton = event.target.closest(".edit-certificate-btn");
    const deleteButton = event.target.closest(".delete-certificate-btn");

    if (!editButton && !deleteButton) {
        return;
    }

    const id = (editButton || deleteButton).dataset.certificateId;

    const rows = await loadCertificates();

    const row = rows.find(item => String(item.id) === String(id));

    if (!row) {
        adminMessage("Certificate not found.", "error");
        return;
    }

    if (editButton) {
        const form = $("certificateAdminForm");

        if (form) {
            form.dataset.editingId = String(row.id);
        }

        const titleInput = $("certTitleInput");
        const urlInput = $("certUrlInput");
        const descriptionInput = $("certDescriptionInput");

        if (titleInput) {
            titleInput.value = row.title || "";
        }

        if (urlInput) {
            urlInput.value = row.url || "";
        }

        if (descriptionInput) {
            descriptionInput.value = row.description || "";
        }

        const saveBtn = $("saveCertBtn");

        if (saveBtn) {
            saveBtn.innerHTML =
                `<i class="fa-solid fa-check mr-2"></i>Save changes`;
        }

        $("cancelCertEditBtn")?.classList.remove("hidden");

        adminMessage(`Editing "${row.title}".`, "info");

        form?.scrollIntoView({ behavior: "smooth", block: "center" });

        return;
    }

    const confirmed = window.confirm(
        `Delete certificate "${row.title}"?`
    );

    if (!confirmed) {
        return;
    }

    try {
        await deleteCertificate(row.id);

        adminMessage("Certificate deleted.", "success");

        await renderAdminCertificateList();
    } catch (error) {
        console.error("Certificate delete error:", error);

        adminMessage(
            error?.message || "Could not delete the certificate.",
            "error"
        );
    }
}

// =====================================================
// Events (wired once from events.js)
// =====================================================

export function setupCertificateEvents() {
    if (eventsWired) {
        return;
    }

    eventsWired = true;

    $("certificateAdminForm")?.addEventListener(
        "submit",
        handleCertificateSubmit
    );

    $("cancelCertEditBtn")?.addEventListener("click", () => {
        resetCertificateForm();
        adminMessage("");
    });

    $("adminCertificatesList")?.addEventListener(
        "click",
        handleCertificateListClick
    );

    $("refreshCertificatesBtn")?.addEventListener("click", () => {
        renderAdminCertificateList().catch(error => {
            console.error("Certificates refresh error:", error);
        });
    });
}

// Called once the app knows who is logged in.
export async function initCertificates() {
    setupCertificateEvents();

    if ($("certificatesList")) {
        await renderCertificatesPage();
    }

    if ($("adminCertificatesList")) {
        await renderAdminCertificateList();
    }
}
