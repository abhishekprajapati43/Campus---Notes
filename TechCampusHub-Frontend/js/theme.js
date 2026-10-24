// =====================================================
// TechCampus_Hub
// theme.js
//
// Light / dark appearance. The <html> class itself is set
// by a tiny inline script in every page's <head> (before
// first paint, so there is never a light flash); this module
// owns the user-facing toggle, persistence and the browser
// chrome colour.
//
// Preference order:
//   1. what the visitor chose (localStorage "tch-theme")
//   2. the OS setting on the very first visit
// =====================================================

const STORAGE_KEY = "tch-theme";

const THEME_COLOR = {
    light: "#102a56",
    dark: "#0b1120"
};

/** @returns {"dark"|"light"} the theme currently applied to <html>. */
export function currentTheme() {
    return document.documentElement.classList.contains("dark")
        ? "dark"
        : "light";
}

/**
 * Applies a theme.
 *
 * @param {"dark"|"light"} theme
 * @param {boolean} persist - false to preview without saving the choice
 *                             (used once on load to sync aria/meta).
 */
export function setTheme(theme, persist = true) {
    const dark = theme === "dark";

    document.documentElement.classList.toggle("dark", dark);

    if (persist) {
        try {
            localStorage.setItem(STORAGE_KEY, dark ? "dark" : "light");
        } catch (error) {
            // Private browsing: the choice simply lasts this session.
        }
    }

    const meta = document.querySelector('meta[name="theme-color"]');

    if (meta) {
        meta.setAttribute("content", dark ? THEME_COLOR.dark : THEME_COLOR.light);
    }

    const label = dark
        ? "Switch to light mode"
        : "Switch to dark mode";

    document.querySelectorAll(".theme-toggle").forEach(button => {
        button.setAttribute("aria-label", label);
        button.setAttribute("title", label);
        button.setAttribute("aria-pressed", String(dark));
    });
}

/**
 * Wires every .theme-toggle on the page. Idempotent: it may be called again
 * after the shared header partial is injected into protected pages.
 */
export function initThemeToggle() {
    document.querySelectorAll(".theme-toggle").forEach(button => {
        if (button.dataset.themeWired === "true") {
            return;
        }

        button.dataset.themeWired = "true";

        button.addEventListener("click", () => {
            setTheme(currentTheme() === "dark" ? "light" : "dark");
        });
    });

    // Sync aria labels / meta theme-color with the class the head script set.
    setTheme(currentTheme(), false);
}
