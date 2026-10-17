// =====================================================
// TechCampus_Hub
// pdfviewer.js
// =====================================================
// Secure in-app PDF viewer used by the student resource
// cards (notes, PYQs, TNP resources, timetables).
//
// The document NEVER arrives as a direct storage URL: its
// bytes are fetched from the authenticated backend relay
// (GET /api/content/{category}/{id}/pdf - JWT travels in
// the Authorization header) and handed to PDF.js as an
// in-memory buffer that renders each page onto a canvas.
// Consequences:
//   - the storage URL is unknown to the browser,
//   - the stream URL itself is useless without the JWT: it
//     cannot be opened in a tab or embedded in an iframe,
//   - there is no download / print / save control anywhere,
//   - the right-click menu is disabled while the viewer is
//     open, and Ctrl+S / Ctrl+P / Ctrl+Shift+S are swallowed
//     so the browser can raise no save/print dialog.
// =====================================================

import { API_BASE_URL } from "./config.js";
import { getAuthToken } from "./api.js";
import { $ } from "./helpers.js";

const PDFJS_LIB_SRC = "./vendor/pdfjs/pdf.min.js";
const PDFJS_WORKER_SRC = "./vendor/pdfjs/pdf.worker.min.js";

// Dashboard category -> backend content segment.
const STREAM_CATEGORY = {
    notes: "notes",
    pyq: "pyqs",
    tnp: "tnp-notes",
    timetable: "timetables"
};

const MIN_SCALE = 0.5;
const MAX_SCALE = 4;
const ZOOM_STEP = 0.25;

const state = {
    ready: false,        // modal + global guards wired
    pdfjsPromise: null,  // lazy <script> load of PDF.js
    doc: null,           // loaded PDFDocumentProxy
    page: 1,
    scale: 1,
    renderTask: null,
    open: false
};

// =====================================================
// PDF.js (lazy, vendored)
// =====================================================

function loadPdfJs() {
    if (window.pdfjsLib) {
        return Promise.resolve(window.pdfjsLib);
    }

    if (!state.pdfjsPromise) {
        state.pdfjsPromise = new Promise((resolve, reject) => {
            const script = document.createElement("script");
            script.src = PDFJS_LIB_SRC;
            script.onload = () => {
                if (window.pdfjsLib) {
                    window.pdfjsLib.GlobalWorkerOptions.workerSrc =
                        PDFJS_WORKER_SRC;
                    resolve(window.pdfjsLib);
                } else {
                    reject(new Error("PDF engine failed to initialize."));
                }
            };
            script.onerror = () =>
                reject(new Error("PDF engine could not be loaded."));
            document.head.appendChild(script);
        });
    }

    return state.pdfjsPromise;
}

// =====================================================
// Viewer state helpers
// =====================================================

function showLoading(show) {
    $("pdfViewerLoading")?.classList.toggle("hidden", !show);
}

function showError(message) {
    showLoading(false);
    const errorBox = $("pdfViewerError");
    const errorMessage = $("pdfViewerErrorMessage");
    if (errorMessage) {
        errorMessage.textContent = message;
    }
    errorBox?.classList.remove("hidden");
}

function clearCanvas() {
    const canvas = $("pdfViewerCanvas");
    if (!canvas) {
        return;
    }
    const context = canvas.getContext("2d");
    if (context) {
        context.clearRect(0, 0, canvas.width, canvas.height);
    }
    canvas.width = 0;
    canvas.height = 0;
    canvas.style.width = "";
    canvas.style.height = "";
    canvas.classList.add("hidden");
}

/** Cancel any pending render, destroy the document, reset the chrome. */
function resetViewer() {
    if (state.renderTask) {
        try {
            state.renderTask.cancel();
        } catch {
            // already finished
        }
        state.renderTask = null;
    }
    if (state.doc) {
        try {
            state.doc.destroy();
        } catch {
            // already destroyed
        }
        state.doc = null;
    }

    state.page = 1;
    state.scale = 1;
    clearCanvas();
    $("pdfViewerError")?.classList.add("hidden");
    if ($("pdfViewerPageNum")) {
        $("pdfViewerPageNum").textContent = "0";
    }
    if ($("pdfViewerPageCount")) {
        $("pdfViewerPageCount").textContent = "0";
    }
    if ($("pdfViewerZoomLabel")) {
        $("pdfViewerZoomLabel").textContent = "100%";
    }
    showLoading(false);
}

// =====================================================
// Rendering
// =====================================================

async function renderPage(page) {
    const canvas = $("pdfViewerCanvas");
    const context = canvas?.getContext("2d");
    if (!canvas || !context) {
        return;
    }

    if (state.renderTask) {
        try {
            state.renderTask.cancel();
        } catch {
            // already finished
        }
        state.renderTask = null;
    }

    const dpr = window.devicePixelRatio || 1;
    const viewport = page.getViewport({ scale: state.scale });

    canvas.width = Math.floor(viewport.width * dpr);
    canvas.height = Math.floor(viewport.height * dpr);
    canvas.style.width = `${Math.floor(viewport.width)}px`;
    canvas.style.height = `${Math.floor(viewport.height)}px`;
    canvas.classList.remove("hidden");

    state.renderTask = page.render({
        canvasContext: context,
        viewport,
        transform: dpr === 1 ? null : [dpr, 0, 0, dpr, 0, 0]
    });

    try {
        await state.renderTask.promise;
    } catch {
        // Render was cancelled by a newer render or a closed viewer.
        return;
    }
    state.renderTask = null;

    const pageNum = $("pdfViewerPageNum");
    const zoomLabel = $("pdfViewerZoomLabel");
    if (pageNum) {
        pageNum.textContent = String(state.page);
    }
    if (zoomLabel) {
        zoomLabel.textContent = `${Math.round(state.scale * 100)}%`;
    }
}

async function goToPage(number) {
    if (!state.doc) {
        return;
    }
    const target = Math.min(Math.max(1, number), state.doc.numPages);
    if (target === state.page) {
        return;
    }
    state.page = target;
    showLoading(true);
    try {
        await renderPage(await state.doc.getPage(target));
    } finally {
        showLoading(false);
    }
}

async function applyZoom(delta) {
    if (!state.doc) {
        return;
    }
    state.scale = Math.min(
        MAX_SCALE,
        Math.max(MIN_SCALE, state.scale + delta)
    );
    try {
        await renderPage(await state.doc.getPage(state.page));
    } catch {
        // document was closed mid-zoom
    }
}

// =====================================================
// Open / close
// =====================================================

export async function openPdfViewer({ category, id, title } = {}) {
    setupPdfViewer();

    const modal = $("pdfViewerModal");
    if (!modal || !id) {
        return;
    }

    resetViewer();
    if ($("pdfViewerTitle")) {
        $("pdfViewerTitle").textContent = title || "Document";
    }

    state.open = true;
    modal.classList.remove("hidden");
    modal.classList.add("flex");
    showLoading(true);

    // Same-origin, JWT-protected relay - not a shareable direct link.
    const path = `/api/content/${STREAM_CATEGORY[category] || category}/${id}/pdf`;
    const token = getAuthToken();

    let response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            headers: token ? { Authorization: `Bearer ${token}` } : {}
        });
    } catch {
        showError(
            "The document could not be loaded. Check your connection and try again."
        );
        return;
    }

    if (!response.ok) {
        if (response.status === 401) {
            showError("Your session has expired. Please log in again.");
        } else if (response.status === 403 || response.status === 404) {
            showError("This document is not available with your current access.");
        } else {
            showError("The document could not be loaded right now.");
        }
        return;
    }

    try {
        const pdfjsLib = await loadPdfJs();
        // Bytes live in memory only: no object URL, no link, no cache.
        const data = await response.arrayBuffer();
        state.doc = await pdfjsLib.getDocument({ data }).promise;

        state.page = 1;
        const firstPage = await state.doc.getPage(1);
        const rawViewport = firstPage.getViewport({ scale: 1 });
        const body = $("pdfViewerBody");
        const available = Math.max(
            240,
            (body?.clientWidth || 640) - 48
        );
        state.scale = Math.min(1.5, Math.max(0.5, available / rawViewport.width));

        if ($("pdfViewerPageCount")) {
            $("pdfViewerPageCount").textContent = String(state.doc.numPages);
        }

        await renderPage(firstPage);
        showLoading(false);
    } catch (error) {
        showError(
            error?.message && /engine/i.test(error.message)
                ? error.message
                : "This document could not be opened. It may be damaged."
        );
    }
}

export function closePdfViewer() {
    if (!state.open) {
        return;
    }
    state.open = false;
    resetViewer();
    const modal = $("pdfViewerModal");
    modal?.classList.add("hidden");
    modal?.classList.remove("flex");
}

// =====================================================
// One-time wiring (modal controls, card triggers,
// right-click and save/print shortcut guards)
// =====================================================

export function setupPdfViewer() {
    if (state.ready) {
        return;
    }

    const modal = $("pdfViewerModal");
    if (!modal) {
        return; // page has no modal partial yet - retry later
    }
    state.ready = true;

    $("pdfViewerCloseBtn")?.addEventListener("click", closePdfViewer);
    $("pdfViewerPrevBtn")?.addEventListener("click", () =>
        goToPage(state.page - 1)
    );
    $("pdfViewerNextBtn")?.addEventListener("click", () =>
        goToPage(state.page + 1)
    );
    $("pdfViewerZoomOutBtn")?.addEventListener("click", () =>
        applyZoom(-ZOOM_STEP)
    );
    $("pdfViewerZoomInBtn")?.addEventListener("click", () =>
        applyZoom(ZOOM_STEP)
    );

    // Clicks on the dimmed backdrop close the viewer.
    modal.addEventListener("click", event => {
        if (event.target === modal) {
            closePdfViewer();
        }
    });

    // Card triggers anywhere on the page:
    // <button data-pdf-open data-pdf-category data-pdf-id data-pdf-title>
    document.addEventListener("click", event => {
        const trigger = event.target.closest?.("[data-pdf-open]");
        if (!trigger) {
            return;
        }
        event.preventDefault();
        openPdfViewer({
            category: trigger.dataset.pdfCategory,
            id: trigger.dataset.pdfId,
            title: trigger.dataset.pdfTitle
        });
    });

    // Guards - they only act while the viewer is open. Capture phase,
    // so no other handler can resurrect the blocked behaviour.
    document.addEventListener(
        "contextmenu",
        event => {
            if (state.open) {
                event.preventDefault();
            }
        },
        true
    );

    document.addEventListener(
        "keydown",
        event => {
            if (!state.open) {
                return;
            }
            if (event.key === "Escape") {
                event.preventDefault();
                closePdfViewer();
                return;
            }
            // Ctrl/Cmd + S (save), Ctrl/Cmd + Shift + S (save as),
            // Ctrl/Cmd + P (print) - never raised over the viewer.
            const key = String(event.key || "").toLowerCase();
            if ((event.ctrlKey || event.metaKey)
                    && (key === "s" || key === "p")) {
                event.preventDefault();
                event.stopPropagation();
            }
        },
        true
    );
}
