// =====================================================
// TechCampus_Hub
// smartsearch.js
// =====================================================
// Smart content search for the student dashboard.
//
// Unlike the old client-side search (which only filtered titles
// of already-loaded rows), this box queries the backend full-
// text index over the ACTUAL TEXT of every uploaded PDF the
// student may open (free notes + purchased courses). Each hit
// knows its document, page number and a preview excerpt, and
// "Open" hands the page + keyword to the in-app PDF viewer,
// which jumps to that page and highlights every occurrence.
//
// UX:
//   - live search (debounced) + loading spinner,
//   - autocomplete suggestions while typing,
//   - recent-search chips (localStorage, optional convenience),
//   - loading / no-results / hint states, responsive layout.

import { $, escapeHtml } from "./helpers.js";
import { smartSearch, smartSearchSuggestions } from "./api.js";
import { openPdfViewerAtPage } from "./pdfviewer.js";

const HISTORY_KEY = "campusnotes:smartSearchHistory";
const HISTORY_LIMIT = 6;
const MIN_QUERY_LENGTH = 2;
const SEARCH_DEBOUNCE_MS = 300;
const SUGGEST_DEBOUNCE_MS = 220;
const MAX_RESULTS = 20;

const CATEGORY_LABELS = {
    notes: "Notes",
    pyq: "PYQ",
    tnp: "TNP",
    timetable: "Exam Timetable",
    resources: "Study Material"
};

let searchTimer = null;
let suggestTimer = null;
let searchSequence = 0;
let suggestSequence = 0;

// =====================================================
// SETUP
// =====================================================

export function setupSmartSearch() {
    // Idempotent: wire the delegated handlers once even if a
    // future caller invokes this twice.
    if (setupSmartSearch.wired) {
        return;
    }

    const input = $("smartSearchInput");
    if (!input) {
        return; // not the student dashboard
    }

    setupSmartSearch.wired = true;

    renderHistoryChips();

    input.addEventListener("input", () => {
        const query = input.value.trim();

        hideSuggestions();

        if (query.length < MIN_QUERY_LENGTH) {
            clearResults();
            return;
        }

        debounceSuggest(query);
        debounceSearch(query);
    });

    input.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            const query = input.value.trim();
            if (query.length >= MIN_QUERY_LENGTH) {
                hideSuggestions();
                runSearch(query, { remember: true });
            }
        }

        if (event.key === "Escape") {
            hideSuggestions();
        }
    });

    // Clicking outside closes the suggestions dropdown.
    document.addEventListener("click", event => {
        const wrap = $("smartSearchWrap");
        if (wrap && !wrap.contains(event.target)) {
            hideSuggestions();
        }
    });

    // Result actions (Open buttons) + history chips.
    $("smartSearchResults")?.addEventListener("click", handleResultsClick);
    $("smartSearchHistory")?.addEventListener("click", handleHistoryClick);
}

// =====================================================
// SEARCH
// =====================================================

function debounceSearch(query) {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(
        () => runSearch(query, { remember: false }),
        SEARCH_DEBOUNCE_MS
    );
}

async function runSearch(query, { remember = false } = {}) {
    const results = $("smartSearchResults");
    if (!results) {
        return;
    }

    const sequence = ++searchSequence;
    showLoading(true);

    try {
        const payload = await smartSearch(query, MAX_RESULTS);

        // A newer keystroke already superseded this response.
        if (sequence !== searchSequence) {
            return;
        }

        const hits = Array.isArray(payload) ? payload : [];

        if (remember && hits.length > 0) {
            rememberSearch(query);
        }

        renderResults(hits, query);
    } catch (error) {
        if (sequence !== searchSequence) {
            return;
        }
        renderError();
    } finally {
        if (sequence === searchSequence) {
            showLoading(false);
        }
    }
}

function clearResults() {
    searchSequence++;
    const results = $("smartSearchResults");
    if (results) {
        results.innerHTML = "";
    }
    showLoading(false);
}

function renderResults(hits, query) {
    const results = $("smartSearchResults");
    if (!results) {
        return;
    }

    if (hits.length === 0) {
        results.innerHTML = `
            <div class="rounded-xl border border-dashed border-slate-200 p-8 text-center">
                <i class="fa-solid fa-face-meh text-3xl text-slate-300"></i>
                <p class="mt-3 font-bold text-slate-700">No matches found</p>
                <p class="mt-1 text-sm text-slate-500">
                    Nothing inside your accessible documents matches
                    &ldquo;${escapeHtml(query)}&rdquo;. Try another topic or a shorter keyword.
                </p>
            </div>`;
        return;
    }

    results.innerHTML = `
        <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">
            ${hits.length} page${hits.length === 1 ? "" : "s"} found
        </p>
        ${hits.map(hit => hitCard(hit, query)).join("")}`;
}

function hitCard(hit, query) {
    const categoryLabel =
        CATEGORY_LABELS[hit.category] || "Document";
    const metaBits = [`Subject: ${escapeHtml(hit.subject || "General")}`];
    if (hit.courseTitle) {
        metaBits.push(`Course: ${escapeHtml(hit.courseTitle)}`);
    }
    metaBits.push(`Page: ${Number(hit.pageNumber) || 1}`);

    return `
        <div class="rounded-xl border border-slate-200 p-4 flex items-start gap-3 hover:border-blue-300 hover:bg-blue-50/40 transition">
            <span class="w-10 h-10 shrink-0 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
                <i class="fa-solid fa-file-pdf text-lg"></i>
            </span>
            <div class="min-w-0 flex-1">
                <p class="font-bold text-slate-800 break-words">
                    ${escapeHtml(hit.title || "Document.pdf")}
                </p>
                <p class="mt-0.5 text-xs text-slate-500">
                    ${metaBits.map(escapeHtml).join(" &middot; ")}
                </p>
                <p class="mt-1.5 text-sm text-slate-600">
                    ${highlightPreview(hit.preview || "", hit.keyword || query || "")}
                </p>
                <span class="mt-1 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-slate-500">
                    ${escapeHtml(categoryLabel)}
                </span>
            </div>
            <button
                type="button"
                class="smart-open-btn shrink-0 rounded-lg bg-blue-600 hover:bg-blue-700 px-3 py-2 text-xs font-bold text-white transition"
                data-category="${escapeHtml(hit.category || "")}"
                data-id="${escapeHtml(String(hit.contentId ?? ""))}"
                data-title="${escapeHtml(hit.title || "")}"
                data-page="${escapeHtml(String(hit.pageNumber || 1))}"
                data-keyword="${escapeHtml(hit.keyword || query || "")}"
            >Open</button>
        </div>`;
}

function renderError() {
    const results = $("smartSearchResults");
    if (!results) {
        return;
    }
    results.innerHTML = `
        <div class="rounded-xl border border-dashed border-rose-200 bg-rose-50/50 p-6 text-center">
            <i class="fa-solid fa-triangle-exclamation text-2xl text-rose-400"></i>
            <p class="mt-2 font-bold text-slate-700">Search could not be completed</p>
            <p class="mt-1 text-sm text-slate-500">Please try again in a moment.</p>
        </div>`;
}

function handleResultsClick(event) {
    const button = event.target.closest(".smart-open-btn");
    if (!button) {
        return;
    }

    const { category, id, title, page, keyword } = button.dataset;
    if (!category || !id) {
        return;
    }

    openPdfViewerAtPage(
        { category, id: Number(id), title },
        Number(page) || 1,
        keyword || ""
    );
}

// =====================================================
// SUGGESTIONS
// =====================================================

function debounceSuggest(query) {
    window.clearTimeout(suggestTimer);
    suggestTimer = window.setTimeout(
        () => runSuggest(query),
        SUGGEST_DEBOUNCE_MS
    );
}

async function runSuggest(query) {
    const box = $("smartSearchSuggestions");
    if (!box) {
        return;
    }

    const sequence = ++suggestSequence;

    try {
        const payload = await smartSearchSuggestions(query);

        if (sequence !== suggestSequence) {
            return; // superseded
        }

        const suggestions = Array.isArray(payload) ? payload : [];
        if (suggestions.length === 0) {
            hideSuggestions();
            return;
        }

        box.innerHTML = suggestions.map(item => `
            <button type="button"
                class="smart-suggest-item flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-blue-50">
                <i class="fa-solid fa-magnifying-glass text-xs text-slate-400"></i>
                <span class="truncate">${escapeHtml(item)}</span>
            </button>`).join("");

        box.classList.remove("hidden");

        box.querySelectorAll(".smart-suggest-item").forEach(item => {
            item.addEventListener("click", () => {
                const input = $("smartSearchInput");
                if (input) {
                    input.value = item.querySelector("span")?.textContent || "";
                }
                hideSuggestions();
                runSearch(input?.value?.trim() || "", { remember: true });
            });
        });
    } catch (error) {
        if (sequence === suggestSequence) {
            hideSuggestions();
        }
    }
}

function hideSuggestions() {
    suggestSequence++;
    $("smartSearchSuggestions")?.classList.add("hidden");
}

// =====================================================
// SEARCH HISTORY (localStorage chips)
// =====================================================

function loadHistory() {
    try {
        const parsed = JSON.parse(
            window.localStorage.getItem(HISTORY_KEY) || "[]"
        );
        return Array.isArray(parsed)
            ? parsed.filter(item => typeof item === "string" && item.trim())
            : [];
    } catch (error) {
        return [];
    }
}

function rememberSearch(query) {
    const trimmed = query.trim();
    if (!trimmed) {
        return;
    }

    const history = [
        trimmed,
        ...loadHistory().filter(
            item => item.toLowerCase() !== trimmed.toLowerCase()
        )
    ].slice(0, HISTORY_LIMIT);

    try {
        window.localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    } catch (error) {
        // localStorage unavailable - chips are optional anyway.
    }

    renderHistoryChips();
}

function renderHistoryChips() {
    const bar = $("smartSearchHistory");
    if (!bar) {
        return;
    }

    const history = loadHistory();

    if (history.length === 0) {
        bar.classList.add("hidden");
        bar.innerHTML = "";
        return;
    }

    bar.innerHTML = `
        <span class="text-xs font-semibold text-slate-400">Recent:</span>
        ${history.map(item => `
            <button type="button"
                class="smart-history-chip rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600 hover:border-blue-300 hover:text-blue-600 transition"
                data-query="${escapeHtml(item)}">${escapeHtml(item)}</button>`).join("")}
        <button type="button"
            class="smart-history-clear px-2 py-1 text-xs font-bold text-slate-400 hover:text-rose-500 transition"
            title="Clear recent searches">
            <i class="fa-solid fa-xmark"></i>
        </button>`;
    bar.classList.remove("hidden");
}

function handleHistoryClick(event) {
    const clear = event.target.closest(".smart-history-clear");
    if (clear) {
        try {
            window.localStorage.removeItem(HISTORY_KEY);
        } catch (error) {
            // ignore
        }
        renderHistoryChips();
        return;
    }

    const chip = event.target.closest(".smart-history-chip");
    if (chip) {
        const query = chip.dataset.query || "";
        const input = $("smartSearchInput");
        if (input) {
            input.value = query;
        }
        runSearch(query, { remember: true });
    }
}

// =====================================================
// SMALL UI HELPERS
// =====================================================

function showLoading(isLoading) {
    $("smartSearchSpinner")?.classList.toggle("hidden", !isLoading);
}

/**
 * Escapes the plain-text preview and wraps every keyword occurrence in
 * <mark>, so the result shows why the page matched.
 */
function highlightPreview(preview, keyword) {
    const text = String(preview || "");
    const term = String(keyword || "").trim();

    if (!term) {
        return escapeHtml(text);
    }

    const escapedPattern = term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const parts = text.split(new RegExp(`(${escapedPattern})`, "ig"));

    return parts
        .map((part, index) =>
            index % 2 === 1
                ? `<mark class="rounded bg-amber-200/80 px-0.5">${escapeHtml(part)}</mark>`
                : escapeHtml(part)
        )
        .join("");
}
