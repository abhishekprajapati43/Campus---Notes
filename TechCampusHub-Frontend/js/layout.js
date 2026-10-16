// =====================================================
// TechCampus_Hub
// layout.js
//
// Renders the shared page chrome (sidebar, header,
// modals) on every protected page and marks the
// active navigation item for the current page.
// =====================================================

const NAV_ID_BY_PAGE = {
    dashboard: "dashboardHomeBtn",
    notifications: "notificationsBtn",
    favorites: "favoritesBtn",
    courses: "coursesBtn",
    profile: "sidebarProfileBtn",
    admin: "adminNavBtn",
    // faculty.html lives inside the faculty workspace group,
    // so the Course quick link carries the active state.
    faculty: "facultyCourseNavBtn",
    paymentHistory: "paymentHistoryBtn",
    certificates: "certificatesBtn",
    feedback: "feedbackBtn"
};

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

    applyHeaderBranding(page);

    markActiveNav(page);

    setupSidebarProfileSection();
}

// =====================================================
// Header Branding
// =====================================================

//
// Every page keeps the default TechCampus_Hub brand;
// only the admin dashboard greets the signed-in admin.
// Lookups are null-guarded so shared chrome that was
// trimmed down cannot break the render.
//

function applyHeaderBranding(page) {
    if (page !== "admin") {
        return;
    }

    const headerTitle =
        document.getElementById("headerTitle");

    const headerSubtitle =
        document.getElementById("headerSubtitle");

    if (headerTitle) {
        headerTitle.textContent = "Welcome Admin";
    }

    if (headerSubtitle) {
        headerSubtitle.textContent = "Admin Dashboard";
    }
}

// =====================================================
// Sidebar Profile Accordion
// =====================================================

//
// The Profile row expands/collapses the details card
// nested underneath it instead of navigating, so the
// profile page stays reachable through the compact
// "View full profile" link inside the card. The chosen
// state is stored in localStorage and restored on every
// render so it survives navigation between pages.
//

const SIDEBAR_PROFILE_EXPANDED_KEY =
    "campusnotes:sidebarProfileExpanded";

function setupSidebarProfileSection() {
    const profileBtn =
        document.getElementById("sidebarProfileBtn");

    const details =
        document.getElementById("sidebarProfileDetails");

    if (!profileBtn || !details) {
        return;
    }

    const chevron =
        profileBtn.querySelector(".fa-chevron-down");

    let expanded =
        window.localStorage.getItem(
            SIDEBAR_PROFILE_EXPANDED_KEY
        ) !== "false";

    const applyState = () => {
        details.classList.toggle(
            "hidden",
            !expanded
        );

        chevron?.classList.toggle(
            "rotate-180",
            expanded
        );

        profileBtn.setAttribute(
            "aria-expanded",
            String(expanded)
        );
    };

    applyState();

    profileBtn.addEventListener("click", () => {
        // Toggle the card only; navigation happens
        // through the "View full profile" link.
        expanded = !expanded;

        window.localStorage.setItem(
            SIDEBAR_PROFILE_EXPANDED_KEY,
            String(expanded)
        );

        applyState();
    });
}

function markActiveNav(page) {
    document
        .querySelectorAll("#sidebar .nav-item")
        .forEach(item => {
            item.classList.remove("active");
        });

    const activeId = NAV_ID_BY_PAGE[page];

    if (activeId) {
        document
            .getElementById(activeId)
            ?.classList.add("active");
    }
}
