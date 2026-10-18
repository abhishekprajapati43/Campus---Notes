// =====================================================
// TechCampus_Hub
// notifications.js
//
// Notifications: automatic alerts when new content is
// uploaded (timetable / course / notes / PYQ / TNP),
// admin broadcasts targeted at all / premium / free
// users, the notifications page and the sidebar badge.
//
// Storage: backend /api/notifications (+ /api/admin/
// notifications). The backend filters the audience
// (premium = approved, unexpired purchase) per caller.
// =====================================================

import { $, escapeHtml, formatDate, updateNoticeBox } from "./helpers.js";
import { currentUserRole } from "./state.js";
import { refreshOwnership, hasActivePremium } from "./courses.js";

import {
    apiGet,
    apiPost,
    apiDelete,
    normalizeNotification
} from "./api.js";

// =====================================================
// Constants
// =====================================================

const SEEN_KEY = "campusnotes:notifSeen";
const MAX_LISTED = 100;

const AUDIENCES = ["all", "premium", "free"];
const KINDS = ["general", "timetable", "course", "notes", "pyq", "tnp"];

const KIND_STYLE = {
    general: { icon: "fa-bell", classes: "bg-slate-100 text-slate-600" },
    timetable: { icon: "fa-calendar-days", classes: "bg-amber-50 text-amber-600" },
    course: { icon: "fa-graduation-cap", classes: "bg-blue-50 text-blue-600" },
    notes: { icon: "fa-book-open", classes: "bg-violet-50 text-violet-600" },
    pyq: { icon: "fa-file-pen", classes: "bg-purple-50 text-purple-600" },
    tnp: { icon: "fa-briefcase", classes: "bg-emerald-50 text-emerald-600" }
};

// =====================================================
// Module state
// =====================================================

let notificationsCache = [];
let notificationsLoaded = false;
let eventsWired = false;

// =====================================================
// Helpers
// =====================================================

function isAdminUser() {
    return currentUserRole === "admin";
}

// =====================================================
// Storage
// =====================================================

export async function loadNotifications(force = false) {
    if (notificationsLoaded && !force) {
        return notificationsCache;
    }

    notificationsLoaded = true;
    notificationsCache = [];

    try {
        const rows = await apiGet("/api/notifications");

        notificationsCache = (Array.isArray(rows) ? rows : [])
            .slice(0, MAX_LISTED)
            .map(normalizeNotification);
    } catch (error) {
        console.error("Notifications loading error:", error);
    }

    return notificationsCache;
}

export async function createNotification({
    title,
    body = "",
    kind = "general",
    audience = "all",
    createdBy = ""
}) {
    const finalTitle = String(title || "").trim();

    if (!finalTitle) {
        throw new Error("Notification title cannot be empty.");
    }

    const data = await apiPost(
        "/api/admin/notifications",
        {
            title: finalTitle,
            message: String(body || "").trim(),
            category: KINDS.includes(kind) ? kind : "general",
            audience: AUDIENCES.includes(audience) ? audience : "all"
        }
    );

    const row = normalizeNotification(data);

    notificationsCache.unshift(row);

    return { row, mode: "cloud" };
}

export async function deleteNotification(notificationId) {
    await apiDelete(
        `/api/admin/notifications/${notificationId}`
    );

    notificationsCache = notificationsCache.filter(
        row => String(row.id) !== String(notificationId)
    );

    await updateNotificationsBadge();
}

// Called by admin.js / courses.js after new content lands.
export async function notifyContentUpload(kind, title, detail = "") {
    try {
        const label = {
            timetable: "New timetable uploaded",
            course: "New course launched",
            notes: "New notes uploaded",
            pyq: "New PYQs added",
            tnp: "New TNP notes uploaded"
        }[kind] || "New content uploaded";

        await createNotification({
            title: label,
            body: detail || title || "",
            kind,
            audience: "all"
        });
    } catch (error) {
        // Never block an upload because the alert failed.
        console.warn("Upload notification failed:", error);
    }
}

// =====================================================
// Audience filtering
// =====================================================

export async function getVisibleNotifications() {
    await loadNotifications();

    const admin = isAdminUser();

    if (admin) {
        return notificationsCache;
    }

    // Ownership must be known before premium/free filtering.
    await refreshOwnership();

    const premium = hasActivePremium();

    return notificationsCache.filter(row => {
        const audience = row.audience || "all";

        if (audience === "all") {
            return true;
        }

        if (audience === "premium") {
            return premium;
        }

        return !premium;
    });
}

// =====================================================
// Sidebar badge (count of unseen notifications)
// =====================================================

export function unreadCount(rows = notificationsCache) {
    const seenAt = Number(
        window.localStorage.getItem(SEEN_KEY) || 0
    );

    return rows.filter(
        row => new Date(row.created_at).getTime() > seenAt
    ).length;
}

// The badge counts only notifications this student can
// actually see (audience: all / premium / free).
export async function updateNotificationsBadge() {
    const badge = $("notificationBadge");

    if (!badge) {
        return;
    }

    let count = 0;

    try {
        const rows = await getVisibleNotifications();

        count = unreadCount(rows);
    } catch (error) {
        console.warn("Notification badge update failed:", error);

        count = unreadCount(notificationsCache);
    }

    badge.textContent = String(count);
    badge.classList.toggle("hidden", count === 0);
    badge.classList.toggle("flex", count > 0);
}

export function markNotificationsSeen() {
    try {
        window.localStorage.setItem(
            SEEN_KEY,
            String(Date.now())
        );
    } catch (error) {
        console.warn("Could not persist notification seen state:", error);
    }

    updateNotificationsBadge().catch(() => {});
}

// =====================================================
// Notifications page (notifications.html)
// =====================================================

export async function renderNotificationsPage() {
    const list = $("notificationsList");
    const empty = $("notificationsEmptyState");

    if (!list) {
        return;
    }

    const rows = await getVisibleNotifications();

    if (empty) {
        empty.classList.toggle("hidden", rows.length > 0);
    }

    // Remove previously rendered entries (keep the empty node).
    list.querySelectorAll("[data-notification-card]").forEach(
        node => node.remove()
    );

    if (!rows.length) {
        markNotificationsSeen();
        return;
    }

    const html = rows.map(row => {
        const style = KIND_STYLE[row.kind] || KIND_STYLE.general;

        return `
            <article data-notification-card
                class="flex items-start gap-4 rounded-xl border border-slate-100
                       bg-white p-4">
                <div class="flex h-11 w-11 shrink-0 items-center justify-center
                            rounded-xl ${style.classes}">
                    <i class="fa-solid ${style.icon}"></i>
                </div>
                <div class="min-w-0 flex-1">
                    <div class="flex flex-wrap items-center gap-2">
                        <p class="font-bold text-slate-800">
                            ${escapeHtml(row.title || "Notification")}
                        </p>
                        ${row.audience && row.audience !== "all"
                            ? `<span class="rounded-full bg-slate-100 px-2 py-0.5
                                 text-xs font-bold uppercase text-slate-500">
                                   ${escapeHtml(row.audience)}
                               </span>`
                            : ""}
                    </div>
                    ${row.body
                        ? `<p class="mt-1 text-sm text-slate-500">
                               ${escapeHtml(row.body)}
                           </p>`
                        : ""}
                    <p class="mt-2 text-xs text-slate-400">
                        ${escapeHtml(formatDate(row.created_at))}
                    </p>
                </div>
            </article>
        `;
    }).join("");

    list.insertAdjacentHTML("beforeend", html);

    markNotificationsSeen();
}

// =====================================================
// Admin — send broadcast + manage list (admin.html)
// =====================================================

function adminMessage(text, type = "info") {
    updateNoticeBox("notificationAdminMessage", text, type, {
        infoClasses: ["bg-emerald-50", "text-emerald-700"]
    });
}

async function renderAdminNotificationList() {
    const list = $("adminNotificationsList");

    if (!list) {
        return;
    }

    await loadNotifications(true);

    if (!notificationsCache.length) {
        list.innerHTML = `
            <p class="text-sm text-slate-500">No notifications sent yet.</p>
        `;
        return;
    }

    list.innerHTML = notificationsCache.map(row => `
        <div class="border rounded-xl p-4 flex flex-wrap items-center gap-4 justify-between">
            <div class="min-w-0">
                <p class="font-bold text-slate-800 truncate">
                    ${escapeHtml(row.title || "Notification")}
                </p>
                <p class="text-sm text-slate-500 truncate">
                    ${escapeHtml(row.body || "")}
                </p>
                <p class="text-xs text-slate-400 mt-1">
                    ${escapeHtml(row.audience || "all")} ·
                    ${escapeHtml(formatDate(row.created_at))}
                </p>
            </div>
            <button type="button"
                class="delete-notification-btn rounded-lg bg-red-50 hover:bg-red-100
                       text-red-600 text-xs font-bold px-3 py-2"
                data-notification-id="${escapeHtml(String(row.id))}">
                Delete
            </button>
        </div>
    `).join("");
}

async function handleSendNotification(event) {
    event.preventDefault();

    const titleInput = $("notifTitleInput");
    const bodyInput = $("notifBodyInput");
    const audienceSelect = $("notifAudienceSelect");

    try {
        const result = await createNotification({
            title: titleInput?.value || "",
            body: bodyInput?.value || "",
            kind: $("notifKindSelect")?.value || "general",
            audience: audienceSelect?.value || "all"
        });

        adminMessage(
            result.mode === "local"
                ? "Notification saved locally — the backend is unreachable."
                : "Notification sent.",
            "success"
        );

        if (titleInput) {
            titleInput.value = "";
        }

        if (bodyInput) {
            bodyInput.value = "";
        }

        await renderAdminNotificationList();
        await updateNotificationsBadge();
    } catch (error) {
        console.error("Notification send error:", error);

        adminMessage(
            error?.message || "Could not send the notification.",
            "error"
        );
    }
}

// =====================================================
// Events (wired once from events.js)
// =====================================================

export function setupNotificationEvents() {
    if (eventsWired) {
        return;
    }

    eventsWired = true;

    // Admin: send form.
    $("notificationSendForm")?.addEventListener(
        "submit",
        handleSendNotification
    );

    // Admin: delete a sent notification.
    $("adminNotificationsList")?.addEventListener("click", async event => {
        const button = event.target.closest(".delete-notification-btn");

        if (!button) {
            return;
        }

        button.disabled = true;

        try {
            await deleteNotification(button.dataset.notificationId);
            await renderAdminNotificationList();
        } catch (error) {
            console.error("Notification delete error:", error);
            button.disabled = false;
        }
    });

    $("refreshNotificationsBtn")?.addEventListener("click", () => {
        renderAdminNotificationList().catch(error => {
            console.error("Admin notifications render error:", error);
        });
    });
}

// Called once the app knows who is logged in.
export async function initNotifications() {
    setupNotificationEvents();

    await loadNotifications(true);

    if ($("notificationsList")) {
        await renderNotificationsPage();
    } else {
        await updateNotificationsBadge();
    }

    if ($("adminNotificationsList")) {
        await renderAdminNotificationList();
    }
}
