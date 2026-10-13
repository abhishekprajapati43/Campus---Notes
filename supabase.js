
// =====================================================
// TechCampus_Hub
// supabase.js
// =====================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";


// -----------------------------------------------------
// Supabase Project Configuration
// -----------------------------------------------------

const supabaseUrl = "https://tauxcecmmuwiugypcxsy.supabase.co";

const supabaseKey =
    "sb_publishable_LLqh8M02X-2hGixiKlml7Q_2QuUXAIc";


// -----------------------------------------------------
// Supabase Client
// -----------------------------------------------------

export const supabase = createClient(
    supabaseUrl,
    supabaseKey
);

// =====================================================
// TEMPORARY DEV BYPASS — removed after verification.
// Enable with localStorage.setItem("__DEV_BYPASS", "1").
// =====================================================

if (
    typeof window !== "undefined" &&
    window.localStorage.getItem("__DEV_BYPASS")
) {
    const fakeUser = {
        id: "dev-bypass-user",
        email: "dev.student@example.com",
        user_metadata: {
            name: "Dev Student"
        }
    };

    supabase.auth.getSession = async () => ({
        data: {
            session: {
                user: fakeUser
            }
        },
        error: null
    });

    supabase.auth.getUser = async () => ({
        data: {
            user: fakeUser
        },
        error: null
    });
}