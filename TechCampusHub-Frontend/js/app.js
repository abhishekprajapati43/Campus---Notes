// =====================================================
// TechCampus_Hub
// app.js
// =====================================================

import { setupEvents } from "./events.js";
import { initializeApp, handleCategoryChange } from "./ui.js";
import { renderLayout } from "./layout.js";
import { getSession } from "./auth.js";
import { refreshOwnership } from "./courses.js";
import { initFavorites } from "./favorites.js";
import { initNotifications } from "./notifications.js";
import { initCertificates } from "./certificates.js";
import { initFeedback } from "./feedback.js";
import { initChatbot } from "./chatbot.js";
import { initFaculty, initHeaderProfileMenu, initChangePasswordModal } from "./faculty.js";
import { setupPdfViewer } from "./pdfviewer.js";

const PAGE =
    document.body?.dataset?.page || "auth";

const VALID_CATEGORIES = [
    "notes",
    "tnp",
    "pyq",
    "timetable"
];

// =====================================================
// Start Application
// =====================================================

document.addEventListener(
    "DOMContentLoaded",
    async () => {
        try {
            // ------------------------------------------------
            // Protected pages: require a session, then render
            // the shared sidebar/header chrome.
            // ------------------------------------------------

            if (PAGE !== "auth" && PAGE !== "home") {
                const { data, error } = await getSession();

                if (error || !data?.session?.user) {
                    window.location.replace("./login.html");
                    return;
                }

                await renderLayout(PAGE);

                // Load the student's approved courses before any
                // notes render, so paid bundles lock correctly.
                await refreshOwnership();
            }

            // Setup all application events first.
            setupEvents();

            // Then initialize authentication,
            // profile and dashboard.
            await initializeApp();

            // ------------------------------------------------
            // Feature modules: favourites, notifications,
            // certificates, feedback and the chatbot.
            // ------------------------------------------------

            if (PAGE !== "auth" && PAGE !== "home") {
                // Header profile menu (photo + name + dropdown):
                // wired once for every page that renders the
                // shared header, so faculty chrome can never show
                // up live without its handlers (self-guarded).
                initHeaderProfileMenu();

                // Shared change-password modal (header dropdown
                // + faculty Profile panel button).
                initChangePasswordModal();

                // Secure in-app PDF viewer: wires the shared
                // modal + the student card triggers once
                // (self-guarded, no-ops when the modal is absent).
                setupPdfViewer();

                await initFavorites();
                await initNotifications();
                await initCertificates();
                await initFeedback();
                await initChatbot();

                // Faculty workspace (self-guarded: only wires
                // and loads on faculty.html for faculty users).
                await initFaculty();
            }

            // ------------------------------------------------
            // Deep link: dashboard.html?category=notes
            // ------------------------------------------------

            if (PAGE === "dashboard") {
                const category =
                    new URLSearchParams(window.location.search)
                        .get("category");

                if (category && VALID_CATEGORIES.includes(category)) {
                    await handleCategoryChange(category);

                    // Keep card active states in sync.
                    document
                        .querySelectorAll("[data-dashboard-category]")
                        .forEach(card => {
                            card.classList.toggle(
                                "active",
                                card.dataset.dashboardCategory === category
                            );
                        });
                }
            }

        } catch (error) {
            console.error(
                "Application initialization error:",
                error
            );
        }
    }
);
