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

    markActiveNav(page);
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
