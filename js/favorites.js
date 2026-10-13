// =====================================================
// TechCampus_Hub
// favorites.js
//
// Favourite notes/TNP resources: heart toggle on the
// resource cards, sidebar badge, and the dedicated
// favourites page.
//
// Storage: Supabase `favorites` table created by
// sql/setup.sql (Part 2). When the table is missing the
// module falls back to this-browser-only storage so the
// feature still works before the migration is run.
// =====================================================

import { supabase } from "../supabase.js";
import { $, escapeHtml, formatDate } from "./helpers.js";
import { currentStudent } from "./state.js";

// =====================================================
// Constants
// =====================================================

const LOCAL_KEY = "campusnotes:favorites";

// Categories a student may favourite.
const FAV_CATEGORIES = ["notes", "tnp"];

// =====================================================
// Module state
// =====================================================

let favoritesCache = [];
let favoritesLoaded = false;
let eventsWired = false;

// =====================================================
// Helpers
// =====================================================

function isMissingTable(error) {
    if (!error) {
        return false;
    }

    return (
        error.code === "PGRST205" ||
        error.code === "42P01" ||
        /could not find the table|does not exist|schema cache/i.test(
            String(error.message || "")
        )
    );
}

function localRead(key, fallback) {
    try {
        const raw = window.localStorage.getItem(key);
        return raw ? JSON.parse(raw) : fallback;
    } catch (error) {
        console.warn("Local storage read failed:", error);
        return fallback;
    }
}

function localWrite(key, value) {
    try {
        window.localStorage.setItem(key, JSON.stringify(value));
        return true;
    } catch (error) {
        console.error("Local storage write failed:", error);
        return false;
    }
}

async function getIdentity() {
    const { data } = await supabase.auth.getSession();

    const user = data?.session?.user || null;

    return {
        id: user?.id || null,
        email: user?.email || currentStudent?.email || "",
        name: currentStudent?.name || user?.user_metadata?.name || ""
    };
}

function normalize(item) {
    return {
        category: String(item.category || "notes"),
        item_id: String(item.item_id || item.id || ""),
        item_title: String(item.item_title || item.title || "Untitled")
    };
}

// =====================================================
// Storage
// =====================================================

export async function loadFavorites(force = false) {
    if (favoritesLoaded && !force) {
        return favoritesCache;
    }

    favoritesLoaded = true;
    favoritesCache = [];

    const identity = await getIdentity();

    if (!identity.id) {
        return favoritesCache;
    }

    const {
        data,
        error
    } = await supabase
        .from("favorites")
        .select("*")
        .eq("user_id", identity.id)
        .order("created_at", { ascending: false });

    if (error) {
        if (!isMissingTable(error)) {
            console.error("Favorites loading error:", error);
        }

        favoritesCache = localRead(LOCAL_KEY, []).filter(
            row => String(row.user_id) === String(identity.id)
        );

        return favoritesCache;
    }

    favoritesCache = data || [];

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

export function favoriteCount() {
    return favoritesCache.length;
}

export async function toggleFavorite({ category, itemId, itemTitle }) {
    const normalizedCategory = FAV_CATEGORIES.includes(category)
        ? category
        : "notes";

    const identity = await getIdentity();

    if (!identity.id) {
        throw new Error("Please log in to save favourites.");
    }

    const already = isFavorite(normalizedCategory, itemId);

    if (already) {
        const {
            error
        } = await supabase
            .from("favorites")
            .delete()
            .eq("user_id", identity.id)
            .eq("category", normalizedCategory)
            .eq("item_id", String(itemId));

        if (error && !isMissingTable(error)) {
            throw error;
        }

        favoritesCache = favoritesCache.filter(
            row =>
                !(
                    row.category === normalizedCategory &&
                    String(row.item_id) === String(itemId)
                )
        );

        if (isMissingTable(error)) {
            writeLocalFavorites(identity.id);
        }
    } else {
        const row = {
            user_id: identity.id,
            category: normalizedCategory,
            item_id: String(itemId),
            item_title: String(itemTitle || "Untitled"),
            created_at: new Date().toISOString()
        };

        const {
            error
        } = await supabase.from("favorites").insert(row);

        if (error && !isMissingTable(error)) {
            throw error;
        }

        if (isMissingTable(error)) {
            favoritesCache.unshift({ ...row, id: `local-${Date.now()}` });
            writeLocalFavorites(identity.id);
        } else {
            favoritesCache.unshift(row);
        }
    }

    syncFavoriteButtons();
    updateFavoritesBadge();

    return !already;
}

function writeLocalFavorites(userId) {
    const all = localRead(LOCAL_KEY, []);

    const mine = favoritesCache.map(row => ({
        ...row,
        user_id: userId
    }));

    const others = all.filter(
        row => String(row.user_id) !== String(userId)
    );

    localWrite(LOCAL_KEY, [...mine, ...others]);
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

    const rows = favoritesCache.filter(
        row => row.category === "notes" || row.category === "tnp"
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

    const label = {
        notes: `<span class="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-700">Note</span>`,
        tnp: `<span class="rounded-full bg-purple-50 px-2.5 py-1 text-xs font-bold text-purple-700">TNP</span>`
    };

    const html = rows.map(row => {
        const category = row.category === "tnp" ? "tnp" : "notes";

        return `
            <article data-favorite-card
                class="resource-card rounded-2xl border border-gray-200
                       bg-white p-5 shadow-sm">
                <div class="flex items-start justify-between gap-3">
                    <div class="flex min-w-0 items-start gap-3">
                        <div class="flex h-11 w-11 shrink-0 items-center justify-center
                                    rounded-xl ${category === "tnp"
                                        ? "bg-purple-50 text-purple-600"
                                        : "bg-blue-50 text-blue-600"}">
                            <i class="fa-solid ${category === "tnp"
                                ? "fa-briefcase"
                                : "fa-book-open"}"></i>
                        </div>
                        <div class="min-w-0">
                            <h3 class="truncate text-base font-semibold text-gray-900"
                                title="${escapeHtml(row.item_title || "Untitled")}">
                                ${escapeHtml(row.item_title || "Untitled")}
                            </h3>
                            <p class="mt-1 text-xs text-slate-400">
                                Saved ${escapeHtml(formatDate(row.created_at))}
                            </p>
                        </div>
                    </div>
                    ${label[category] || ""}
                </div>
                <div class="mt-4 flex items-center gap-2">
                    <a href="./dashboard.html?category=${category}"
                       class="inline-flex items-center gap-2 rounded-lg bg-blue-600
                              px-3 py-2 text-sm font-medium text-white transition
                              hover:bg-blue-700">
                        <i class="fa-solid fa-arrow-up-right-from-square"></i>
                        Open
                    </a>
                    <button type="button"
                        class="remove-favorite-btn inline-flex items-center gap-2
                               rounded-lg bg-red-50 px-3 py-2 text-sm font-medium
                               text-red-600 transition hover:bg-red-100"
                        data-favorite-category="${escapeHtml(category)}"
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

    await loadFavorites(true);

    syncFavoriteButtons();
    updateFavoritesBadge();

    if ($("favoritesList")) {
        await renderFavoritesPage();
    }
}
