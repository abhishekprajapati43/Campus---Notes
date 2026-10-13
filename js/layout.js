// =====================================================
// TechCampus_Hub
// layout.js
//
// Renders the shared page chrome (sidebar, header,
// modals) on every protected page and marks the
// active navigation item for the current page.
// =====================================================

import { updateProfileDropdown } from "./ui.js";

const NAV_ID_BY_PAGE = {
    dashboard: "dashboardHomeBtn",
    notifications: "notificationsBtn",
    favorites: "favoritesBtn",
    courses: "coursesBtn",
    settings: "settingsBtn",
    profile: "sidebarProfileBtn",
    admin: "adminNavBtn",
    paymentHistory: "paymentHistoryBtn",
    certificates: "certificatesBtn",
    feedback: "feedbackBtn"
};

const VALID_CATEGORIES = [
    "notes",
    "tnp",
    "pyq",
    "timetable"
];

async function fetchPartial(path) {
    const response = await fetch(path);

    if (!response.ok) {
        throw new Error(
            `Could not load ${path} (${response.status})`
        );
    }

    return await response.text();
}

export async function renderLayout(page) {
    const chrome = document.getElementById("pageChrome");
    const headerSlot = document.getElementById("pageHeader");

    if (!chrome || !headerSlot) {
        return;
    }

    const [sidebarHtml, modalsHtml, headerHtml] =
        await Promise.all([
            fetchPartial("./partials/sidebar.html"),
            fetchPartial("./partials/modals.html"),
            fetchPartial("./partials/header.html")
        ]);

    chrome.innerHTML =
        `${sidebarHtml}\n${modalsHtml}`;

    headerSlot.innerHTML = headerHtml;

    markActiveNav(page);

    // Wire the header profile dropdown (guarded once
    // inside updateProfileDropdown).
    updateProfileDropdown();
}

function markActiveNav(page) {
    document
        .querySelectorAll("#sidebar .nav-item")
        .forEach(item => {
            item.classList.remove("active");
        });

    // dashboard.html?category=notes highlights the
    // matching sidebar category link.
    if (page === "dashboard") {
        const category =
            new URLSearchParams(window.location.search)
                .get("category");

        if (category && VALID_CATEGORIES.includes(category)) {
            const link = document.querySelector(
                `#sidebar [data-nav-category="${category}"]`
            );

            if (link) {
                link.classList.add("active");
                return;
            }
        }
    }

    const activeId = NAV_ID_BY_PAGE[page];

    if (activeId) {
        document
            .getElementById(activeId)
            ?.classList.add("active");
    }
}
