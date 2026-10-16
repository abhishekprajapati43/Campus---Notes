// =====================================================
// TechCampus_Hub
// admin-users.js
//
// Admin panel → Users: every registered account (students, faculty,
// admins) with their role, purchase status and premium validity.
// =====================================================

import { reloadStudents } from "./data.js";
import {
    loadAllPurchases,
    purchaseExpired,
    formatExpiryDateTime
} from "./courses.js";
import { $, escapeHtml } from "./helpers.js";
import {
    setUserEnabled,
    adminDeleteUser,
    getStoredUser
} from "./api.js";

// =====================================================
// Rendering
// =====================================================

export async function renderAdminUsers() {
    const list = $("adminUsersList");

    if (!list) {
        return;
    }

    list.innerHTML = `
        <p class="text-sm text-slate-500">Loading users…</p>
    `;

    let students = [];

    try {
        students = await reloadStudents();
    } catch (error) {
        console.error("Users loading error:", error);
    }

    const { rows: purchases } = await loadAllPurchases();

    if (!students.length) {
        list.innerHTML = `
            <p class="text-sm text-slate-500">
                No students found (register an account to see it here).
            </p>
        `;
        return;
    }

    const me = getStoredUser();

    list.innerHTML = students.map(student => {
        const mine = purchases.filter(row =>
            String(row.user_id || "").toLowerCase() ===
                String(student.id || "").toLowerCase()
            ||
            (
                row.user_email &&
                student.email &&
                row.user_email.toLowerCase() ===
                    student.email.toLowerCase()
            )
        );

        const active = mine.filter(
            row => row.status === "approved" && !purchaseExpired(row)
        );
        const expired = mine.filter(
            row => row.status === "approved" && purchaseExpired(row)
        );
        const pending = mine.filter(row => row.status === "pending");

        const suspended = student.enabled === false;
        const isSelf = me && String(me.id) === String(student.id);

        let accessBadge = `
            <span class="rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-500">
                Free user
            </span>
        `;

        let validityLine = "";

        if (active.length) {
            accessBadge = `
                <span class="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700">
                    Premium
                </span>
            `;

            const latest = active
                .map(row => row.expires_at)
                .filter(Boolean)
                .sort()
                .pop();

            validityLine = latest
                ? `Valid until ${formatExpiryDateTime(latest)}`
                : "No expiry";
        } else if (expired.length) {
            accessBadge = `
                <span class="rounded-lg bg-red-50 px-2.5 py-1 text-xs font-bold text-red-600">
                    Expired
                </span>
            `;
        }

        const suspendedBadge = suspended
            ? `
                <span class="rounded-lg bg-red-50 px-2.5 py-1 text-xs font-bold text-red-600">
                    Suspended
                </span>
            `
            : "";

        const roleBadge = student.role === "admin"
            ? `
                <span class="rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-700">
                    Admin
                </span>
            `
            : student.role === "faculty"
                ? `
                    <span class="rounded-lg bg-violet-100 px-2.5 py-1 text-xs font-bold text-violet-700">
                        Faculty
                    </span>
                `
                : "";

        // Faculty sign-up declaration — shown for faculty accounts only.
        const declarationBadge = student.role === "faculty"
            ? student.declaration_accepted
                ? `
                    <span class="rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700">
                        Declaration ✓
                    </span>
                `
                : `
                    <span class="rounded-lg bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-700">
                        Declaration missing
                    </span>
                `
            : "";

        const details = [
            student.course,
            student.branch,
            student.year ? `Year ${student.year}` : "",
            student.semester ? `Sem ${student.semester}` : ""
        ].filter(Boolean).join(" · ");

        // Suspension keeps the data but blocks the login; the signed-in admin
        // cannot suspend themselves (the backend refuses it too).
        const toggleBtn = isSelf
            ? ""
            : suspended
                ? `
                    <button type="button"
                        class="restore-user-btn rounded-lg bg-emerald-50
                               px-2.5 py-1 text-xs font-bold text-emerald-700
                               hover:bg-emerald-100 transition"
                        data-user-id="${escapeHtml(String(student.id))}"
                        data-user-name="${escapeHtml(student.name || student.email || "this user")}">
                        Restore
                    </button>
                `
                : `
                    <button type="button"
                        class="suspend-user-btn rounded-lg bg-amber-50
                               px-2.5 py-1 text-xs font-bold text-amber-700
                               hover:bg-amber-100 transition"
                        data-user-id="${escapeHtml(String(student.id))}"
                        data-user-name="${escapeHtml(student.name || student.email || "this user")}">
                        Remove
                    </button>
                `;

        const deleteBtn = isSelf
            ? ""
            : `
                <button type="button"
                    class="delete-user-btn rounded-lg bg-red-50
                           px-2.5 py-1 text-xs font-bold text-red-600
                           hover:bg-red-100 transition"
                    data-user-id="${escapeHtml(String(student.id))}"
                    data-user-name="${escapeHtml(student.name || student.email || "this user")}">
                    Delete
                </button>
            `;

        return `
            <div class="border rounded-xl p-4 flex flex-wrap items-center gap-4 justify-between">
                <div class="flex items-center gap-3 min-w-0">
                    <div class="w-10 h-10 shrink-0 rounded-full bg-blue-100 text-blue-700
                                flex items-center justify-center">
                        <i class="fa-solid fa-user"></i>
                    </div>
                    <div class="min-w-0">
                        <p class="font-bold text-slate-800 truncate">
                            ${escapeHtml(student.name || "Student")}
                            ${roleBadge}
                            ${declarationBadge}
                            ${suspendedBadge}
                        </p>
                        <p class="text-sm text-slate-500 truncate">
                            ${escapeHtml(student.email || "")}
                        </p>
                        ${details
                            ? `<p class="text-xs text-slate-400 truncate">${escapeHtml(details)}</p>`
                            : ""}
                    </div>
                </div>
                <div class="flex flex-col items-end gap-1">
                    <div class="flex items-center gap-2">
                        ${pending.length
                            ? `<span class="rounded-lg bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-700">
                                   ${pending.length} pending
                               </span>`
                            : ""}
                        ${accessBadge}
                    </div>
                    ${validityLine
                        ? `<p class="text-xs text-slate-400">${escapeHtml(validityLine)}</p>`
                        : ""}
                    <div class="flex items-center gap-2 mt-1">
                        ${toggleBtn}
                        ${deleteBtn}
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

// =====================================================
// Actions: suspend / restore / permanently delete
// =====================================================

function usersMessage(text, type = "info") {
    const box = $("adminUsersMessage");

    if (!box) {
        return;
    }

    box.textContent = text || "";
    box.className = "text-sm min-h-5 mb-3 px-3 py-2 rounded-xl";

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

async function handleUsersAction(event) {
    const button = event.target.closest(
        ".suspend-user-btn, .restore-user-btn, .delete-user-btn"
    );

    if (!button) {
        return;
    }

    const userId = button.dataset.userId;
    const name = button.dataset.userName || "this user";
    const isDelete = button.classList.contains("delete-user-btn");
    const isRestore = button.classList.contains("restore-user-btn");

    // Confirmation dialog before either action.
    const confirmed = isDelete
        ? window.confirm(
            `Permanently delete ${name}'s account?\n\n` +
            "All of their data — profile, purchases, favorites and " +
            "reviews — will be removed. This cannot be undone."
        )
        : window.confirm(
            isRestore
                ? `Restore ${name}'s account? They will be able to log in again.`
                : `Remove ${name}?\n\n` +
                  "Their account will be suspended (they cannot log in) " +
                  "until you restore it. All data is kept."
        );

    if (!confirmed) {
        return;
    }

    button.disabled = true;

    try {
        if (isDelete) {
            await adminDeleteUser(userId);
            usersMessage(`${name}'s account was permanently deleted.`, "success");
        } else {
            await setUserEnabled(userId, isRestore);
            usersMessage(
                isRestore
                    ? `${name}'s account was restored.`
                    : `${name} was removed (suspended).`,
                "success"
            );
        }

        await renderAdminUsers();
    } catch (error) {
        console.error("User management error:", error);
        usersMessage(
            error.message || "The action could not be completed.",
            "error"
        );
        button.disabled = false;
    }
}

// =====================================================
// Wiring (called from events.js setupEvents)
// =====================================================

export function setupAdminUsers() {
    if (!$("adminUsersList")) {
        return;
    }

    renderAdminUsers().catch(error => {
        console.error("Admin users render error:", error);
    });

    $("refreshUsersBtn")?.addEventListener("click", () => {
        renderAdminUsers().catch(error => {
            console.error("Admin users refresh error:", error);
        });
    });

    // Delegated: the row buttons are re-rendered after every action.
    $("adminUsersList")?.addEventListener("click", handleUsersAction);
}
