// =====================================================
// TechCampus_Hub
// favorites.js
//
// Favourite learning resources: heart toggle on the
// resource cards, sidebar badge, and the dedicated
// favourites page.
//
// Supported content types: notes, TNP resources, PYQs
// and exam timetables (itemType NOTE / TNP_NOTE / PYQ /
// TIMETABLE on the backend).
//
// Storage: backend /api/favorites. Each row stores
// userId + itemType + itemId + createdAt, so favourites
// survive refreshes and re-logins. Opening a favourite
// goes through the SAME secure in-app PDF viewer used by
// the dashboard cards (data-pdf-open) - the document
// bytes are pulled through the authenticated backend
// relay, which also enforces bundle gating.
// =====================================================

import { $, escapeHtml, formatDate, getIdentity } from "./helpers.js";

import {
    apiGet,
    apiPost,
    apiDelete,
    normalizeFavorite,
    FAVORITE_CATEGORY_TO_TYPE
} from "./api.js";

// =====================================================
// Module state
// =====================================================

// Categories a student may favourite, with the display
// meta used on the favourites page.
const FAV_CATEGORIES = ["notes", "tnp", "pyq", "timetable"];

const FAV_CATEGORY_META = {
    notes: {
        label: "Note",
        icon: "fa-book-open",
        iconClass: "bg-blue-50 text-blue-600"
    },
    tnp: {
        label: "TNP",
        icon: "fa-briefcase",
        iconClass: "bg-purple-50 text-purple-600"
    },
    pyq: {
        label: "PYQ",
        icon: "fa-file-lines",
        iconClass: "bg-green-50 text-green-600"
    },
    timetable: {
        label: "Timetable",
        icon: "fa-calendar-days",
        iconClass: "bg-orange-50 text-orange-600"
    }
};

let favoritesCache = [];
let favoritesLoaded = false;
let eventsWired = false;

// =====================================================
// Storage
// =====================================================

export async function loadFavorites(force = false) {
    if (favoritesLoaded && !force) {
        return favoritesCache;
    }

    const identity = await getIdentity();

    if (!identity.id) {
        favoritesLoaded = true;
        favoritesCache = [];
        return favoritesCache;
    }

    try {
        const rows = await apiGet("/api/favorites");

        favoritesCache = (Array.isArray(rows) ? rows : [])
            .map(normalizeFavorite)
            .filter(Boolean);

        // Only a successful snapshot flips the "loaded"
        // flag, so a transient failure is retried instead
        // of being cached as "this student has nothing".
        favoritesLoaded = true;
    } catch (error) {
        console.error("Favorites loading error:", error);
    }

    return favoritesCache;
}

export function isFavorite(category, itemId) {
    const wanted = String(itemId);

    return favoritesCache.some(
        row =>
            row.category === String(category) &&
            String(row.item_id) === wanted
    );
}

export async function toggleFavorite({ category, itemId, itemTitle }) {
    const identity = await getIdentity();

    if (!identity.id) {
        throw new Error("Please log in to save favourites.");
    }

    // Never fall back to a default type: writing NOTE for
    // an unknown category would store the favourite under
    // the wrong itemType and the item could never be found
    // or removed again.
    if (!FAV_CATEGORIES.includes(category) || !itemId) {
        throw new Error("This item cannot be saved to favourites.");
    }

    const itemType = FAVORITE_CATEGORY_TO_TYPE[category];

    const already = isFavorite(category, itemId);

    if (already) {
        await apiDelete(
            `/api/favorites/${itemType}/${itemId}`
        );

        favoritesCache = favoritesCache.filter(
            row =>
                !(
                    row.category === category &&
                    String(row.item_id) === String(itemId)
                )
        );
    } else {
        const data = await apiPost(
            "/api/favorites",
            {
                itemType,
                itemId: Number(itemId) || null
            }
        );

        favoritesCache.unshift(
            normalizeFavorite(data) || {
                category,
                item_id: String(itemId),
                item_title: String(itemTitle || "Untitled")
            }
        );
    }

    syncFavoriteButtons();
    updateFavoritesBadge();

    return !already;
}

// =====================================================
// Card heart buttons
// =====================================================

export function favoriteButtonHtml(category, itemId, itemTitle) {
    if (!FAV_CATEGORIES.includes(category) || !itemId) {
        return "";
    }

    return `
        <button
            type="button"
            class="favorite-btn shrink-0 h-9 w-9 rounded-full
                   flex items-center justify-center transition
                   bg-slate-100 text-slate-400 hover:text-red-500"
            data-favorite-category="${escapeHtml(String(category))}"
            data-favorite-id="${escapeHtml(String(itemId))}"
            data-favorite-title="${escapeHtml(String(itemTitle || "Untitled"))}"
            aria-label="Add to favourites"
            title="Add to favourites"
        >
            <i class="fa-regular fa-heart"></i>
        </button>
    `;
}

export function syncFavoriteButtons() {
    document.querySelectorAll(".favorite-btn").forEach(button => {
        const category = button.dataset.favoriteCategory;
        const itemId = button.dataset.favoriteId;
        const active = isFavorite(category, itemId);

        const icon = button.querySelector("i");

        if (icon) {
            icon.className = active ? "fa-solid fa-heart" : "fa-regular fa-heart";
        }

        button.classList.toggle("text-red-500", active);
        button.classList.toggle("bg-red-50", active);
        button.classList.toggle("text-slate-400", !active);
        button.classList.toggle("bg-slate-100", !active);

        button.setAttribute(
            "aria-label",
            active ? "Remove from favourites" : "Add to favourites"
        );
        button.title =
            active ? "Remove from favourites" : "Add to favourites";
    });
}

export function updateFavoritesBadge() {
    const badge = $("favoritesBadge");

    if (!badge) {
        return;
    }

    const count = favoritesCache.length;

    badge.textContent = String(count);
    badge.classList.toggle("hidden", count === 0);
    badge.classList.toggle("flex", count > 0);
}

// =====================================================
// Favourites page
// =====================================================

export async function renderFavoritesPage() {
    const list = $("favoritesList");
    const empty = $("favoritesEmptyState");

    if (!list) {
        return;
    }

    await loadFavorites(true);

    // Every favouritable content type shows up here.
    const rows = favoritesCache.filter(
        row => FAV_CATEGORIES.includes(row.category)
    );

    if (empty) {
        empty.classList.toggle("hidden", rows.length > 0);
    }

    if (!rows.length) {
        list.querySelectorAll("[data-favorite-card]").forEach(
            node => node.remove()
        );
        return;
    }

    const html = rows.map(row => {
        const meta = FAV_CATEGORY_META[row.category]
            || FAV_CATEGORY_META.notes;

        const title = row.item_title || "Untitled";

        return `
            <article data-favorite-card
                class="resource-card rounded-2xl border border-gray-200
                       bg-white p-5 shadow-sm">
                <div class="flex items-start justify-between gap-3">
                    <div class="flex min-w-0 items-start gap-3">
                        <div class="flex h-11 w-11 shrink-0 items-center justify-center
                                    rounded-xl ${meta.iconClass}">
                            <i class="fa-solid ${meta.icon}"></i>
                        </div>
                        <div class="min-w-0">
                            <h3 class="truncate text-base font-semibold text-gray-900"
                                title="${escapeHtml(title)}">
                                ${escapeHtml(title)}
                            </h3>
                            <p class="mt-1 text-xs text-slate-400">
                                Saved ${escapeHtml(formatDate(row.created_at))}
                            </p>
                        </div>
                    </div>
                    <span class="shrink-0 rounded-full bg-slate-100
                                 px-2.5 py-1 text-xs font-bold text-slate-700">
                        ${escapeHtml(meta.label)}
                    </span>
                </div>
                <div class="mt-4 flex flex-wrap items-center gap-2">
                    <button type="button"
                        data-pdf-open
                        data-pdf-category="${escapeHtml(row.category)}"
                        data-pdf-id="${escapeHtml(String(row.item_id))}"
                        data-pdf-title="${escapeHtml(title)}"
                        class="inline-flex items-center gap-2 rounded-lg
                               bg-blue-600 px-3 py-2 text-sm font-medium
                               text-white transition hover:bg-blue-700">
                        <i class="fa-regular fa-eye"></i>
                        Open
                    </button>
                    <button type="button"
                        class="remove-favorite-btn inline-flex items-center gap-2
                               rounded-lg bg-red-50 px-3 py-2 text-sm font-medium
                               text-red-600 transition hover:bg-red-100"
                        data-favorite-category="${escapeHtml(row.category)}"
                        data-favorite-id="${escapeHtml(String(row.item_id))}">
                        <i class="fa-solid fa-heart-crack"></i>
                        Remove
                    </button>
                </div>
            </article>
        `;
    }).join("");

    // Replace previous cards, keep the built-in empty state node.
    list.querySelectorAll("[data-favorite-card]").forEach(
        node => node.remove()
    );
    list.insertAdjacentHTML("beforeend", html);
}

// =====================================================
// Events (wired once from events.js)
// =====================================================

export function setupFavoriteEvents() {
    if (eventsWired) {
        return;
    }

    eventsWired = true;

    // Heart toggle — works on any page that renders cards.
    document.addEventListener("click", async event => {
        const button = event.target.closest(".favorite-btn");

        if (!button) {
            return;
        }

        event.preventDefault();

        button.disabled = true;

        try {
            await toggleFavorite({
                category: button.dataset.favoriteCategory,
                itemId: button.dataset.favoriteId,
                itemTitle: button.dataset.favoriteTitle
            });
        } catch (error) {
            console.error("Favourite toggle error:", error);
        } finally {
            button.disabled = false;
        }
    });

    // Remove button on the favourites page.
    document.addEventListener("click", async event => {
        const button = event.target.closest(".remove-favorite-btn");

        if (!button) {
            return;
        }

        event.preventDefault();

        button.disabled = true;

        try {
            await toggleFavorite({
                category: button.dataset.favoriteCategory,
                itemId: button.dataset.favoriteId,
                itemTitle: ""
            });

            await renderFavoritesPage();
        } catch (error) {
            console.error("Favourite remove error:", error);
            button.disabled = false;
        }
    });
}

// Called once the app knows who is logged in.
export async function initFavorites() {
    setupFavoriteEvents();

    if ($("favoritesList")) {
        // The page renderer force-loads its own fresh snapshot -
        // don't fetch /api/favorites twice on the favorites page.
        await renderFavoritesPage();
    } else {
        await loadFavorites(true);
    }

    syncFavoriteButtons();
    updateFavoritesBadge();
}
