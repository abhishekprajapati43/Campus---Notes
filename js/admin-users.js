// =====================================================
// TechCampus_Hub
// admin-users.js
//
// Admin panel → Users: every registered student with
// their role, purchase status and premium validity.
// =====================================================

import { reloadStudents } from "./data.js";
import {
    loadAllPurchases,
    purchaseExpired,
    formatExpiryDateTime
} from "./courses.js";
import { $, escapeHtml, formatDate } from "./helpers.js";

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
                No students found (run sql/setup.sql for database storage).
            </p>
        `;
        return;
    }

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

        const roleBadge = student.role === "admin"
            ? `
                <span class="rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-700">
                    Admin
                </span>
            `
            : "";

        const details = [
            student.course,
            student.branch,
            student.year ? `Year ${student.year}` : "",
            student.semester ? `Sem ${student.semester}` : ""
        ].filter(Boolean).join(" · ");

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
                </div>
            </div>
        `;
    }).join("");
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
}
