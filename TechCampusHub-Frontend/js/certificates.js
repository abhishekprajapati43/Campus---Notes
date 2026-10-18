// =====================================================
// TechCampus_Hub
// certificates.js
//
// Certification section: students open admin-managed
// certificate links; admins add, edit and delete them.
//
// Storage: backend /api/certificates/links (students see
// active links only; admins see everything — filtered
// server-side). Admin writes go through
// /api/admin/certificates/links.
// =====================================================

import { $, escapeHtml, updateNoticeBox } from "./helpers.js";

import {
    apiGet,
    apiPost,
    apiPut,
    apiDelete,
    normalizeCertificateLink
} from "./api.js";

// =====================================================
// Module state
// =====================================================

let certificatesCache = [];
let certificatesLoaded = false;
let eventsWired = false;

// =====================================================
// Storage
// =====================================================

export async function loadCertificates(force = false) {
    if (certificatesLoaded && !force) {
        return certificatesCache;
    }

    certificatesLoaded = true;
    certificatesCache = [];

    try {
        const rows = await apiGet("/api/certificates/links");

        certificatesCache = (Array.isArray(rows) ? rows : [])
            .map(normalizeCertificateLink);
    } catch (error) {
        console.error("Certificates loading error:", error);
    }

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

    const data = await apiPost(
        "/api/admin/certificates/links",
        {
            title,
            url,
            description: String(fields.description || "").trim(),
            sortOrder: Number(fields.sort_order) || 0,
            isActive: fields.is_active !== false
        }
    );

    const row = normalizeCertificateLink(data);

    certificatesCache.push(row);

    return { row, mode: "cloud" };
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
        patch.isActive = Boolean(fields.is_active);
    }

    await apiPut(
        `/api/admin/certificates/links/${certificateId}`,
        patch
    );

    return { mode: "cloud" };
}

export async function deleteCertificate(certificateId) {
    await apiDelete(
        `/api/admin/certificates/links/${certificateId}`
    );

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
    updateNoticeBox("certificateAdminMessage", text, type, {
        infoClasses: ["bg-emerald-50", "text-emerald-700"]
    });
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
                    ? "Certificate saved locally — the backend is unreachable."
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
