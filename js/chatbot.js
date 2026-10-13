// =====================================================
// TechCampus_Hub
// chatbot.js
//
// AI chatbot entry point: the "Open Gemini" button on
// the dashboard opens the official Google Gemini site,
// and admins can change the link from the admin panel.
//
// Storage: site_settings.gemini_url (sql/setup.sql
// Part 2) with a this-browser fallback.
// =====================================================

import { supabase } from "../supabase.js";
import { $ } from "./helpers.js";

// =====================================================
// Constants
// =====================================================

export const DEFAULT_GEMINI_URL = "https://gemini.google.com";

const LOCAL_KEY = "campusnotes:geminiUrl";

// =====================================================
// Module state
// =====================================================

let eventsWired = false;

// =====================================================
// Storage
// =====================================================

export async function getGeminiUrl() {
    try {
        const {
            data,
            error
        } = await supabase
            .from("site_settings")
            .select("gemini_url")
            .eq("id", 1)
            .maybeSingle();

        if (!error && data?.gemini_url) {
            return data.gemini_url;
        }
    } catch (error) {
        console.warn("Gemini link load failed:", error);
    }

    try {
        const localValue = window.localStorage.getItem(LOCAL_KEY);

        return localValue || DEFAULT_GEMINI_URL;
    } catch (error) {
        return DEFAULT_GEMINI_URL;
    }
}

export async function saveGeminiUrl(value) {
    const trimmed = String(value || "").trim();

    if (!/^https?:\/\//i.test(trimmed)) {
        throw new Error("The Gemini link must start with http:// or https://");
    }

    const {
        error
    } = await supabase
        .from("site_settings")
        .update({ gemini_url: trimmed })
        .eq("id", 1);

    let mode = "cloud";

    if (error) {
        // Missing column/table — keep it in this browser.
        mode = "local";
    }

    try {
        window.localStorage.setItem(LOCAL_KEY, trimmed);
    } catch (storageError) {
        console.warn("Gemini link local save failed:", storageError);
    }

    return { mode, url: trimmed };
}

// =====================================================
// Events (wired once from events.js)
// =====================================================

function settingsMessage(text, type = "info") {
    const box = $("geminiSettingsMessage");

    if (!box) {
        return;
    }

    box.textContent = text || "";
    box.className = "text-sm min-h-5 mt-2";

    if (!text) {
        return;
    }

    if (type === "error") {
        box.classList.add("text-red-600");
    } else {
        box.classList.add("text-emerald-600");
    }
}

export function setupChatbotEvents() {
    if (eventsWired) {
        return;
    }

    eventsWired = true;

    // Dashboard: open the official Gemini website.
    $("openGeminiBtn")?.addEventListener("click", async () => {
        const url = await getGeminiUrl();

        window.open(url, "_blank", "noopener,noreferrer");
    });

    // Admin: change the Gemini link.
    $("updateGeminiLinkBtn")?.addEventListener("click", async () => {
        const input = $("geminiLinkInput");

        if (!input) {
            return;
        }

        try {
            const result = await saveGeminiUrl(input.value);

            settingsMessage(
                result.mode === "local"
                    ? "Chatbot link saved (local — run sql/setup.sql for database storage)."
                    : "Chatbot link updated.",
                "success"
            );
        } catch (error) {
            console.error("Gemini link save error:", error);

            settingsMessage(
                error?.message || "Could not save the chatbot link.",
                "error"
            );
        }
    });
}

// Called once the app knows who is logged in.
export async function initChatbot() {
    setupChatbotEvents();

    const input = $("geminiLinkInput");

    if (input && !input.value) {
        try {
            input.value = await getGeminiUrl();
        } catch (error) {
            console.error("Gemini link load error:", error);
        }
    }
}
