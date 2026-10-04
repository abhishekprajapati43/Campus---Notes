// =====================================================
// TechCampus_Hub
// auth.js
// =====================================================

import { supabase } from "../supabase.js";

// =====================================================
// Student Signup
// =====================================================

export async function signUpStudent({
    email,
    password,
    name,
    course,
    branch,
    year,
    semester
}) {
    return await supabase.auth.signUp({
        email: String(email || "").trim(),
        password,

        options: {
            data: {
                name: String(name || "").trim(),
                course: String(course || "").trim(),
                branch: String(branch || "").trim(),
                year: Number(year),
                semester: Number(semester)
            }
        }
    });
}

// =====================================================
// Sign In
// =====================================================

export async function signIn(email, password) {
    return await supabase.auth.signInWithPassword({
        email: String(email || "").trim(),
        password
    });
}

// =====================================================
// Reset Password
// =====================================================

export async function resetPassword(email) {
    const redirectUrl =
        `${window.location.origin}${window.location.pathname}`;

    return await supabase.auth.resetPasswordForEmail(
        String(email || "").trim(),
        {
            redirectTo: redirectUrl
        }
    );
}

// =====================================================
// Get Current Session
// =====================================================

export async function getSession() {
    return await supabase.auth.getSession();
}

// =====================================================
// Get Current Auth User
// =====================================================

export async function getCurrentUser() {
    const {
        data,
        error
    } = await supabase.auth.getUser();

    return {
        user: data?.user || null,
        error
    };
}

// =====================================================
// Sign Out
// =====================================================

export async function signOut() {
    try {
        console.log("Starting logout...");

        const result = await supabase.auth.signOut({
            scope: "local"
        });

        console.log("Logout result:", result);

        if (result?.error) {
            console.error(
                "Supabase logout error:",
                result.error
            );

            return false;
        }

        console.log("Logout successful");

        return true;

    } catch (error) {
        console.error(
            "Logout exception:",
            error
        );

        return false;
    }
}

// =====================================================
// Auth State Listener
// =====================================================

export function onAuthStateChange(callback) {
    return supabase.auth.onAuthStateChange(callback);
}

// =====================================================
// Delete Current Student Account
// =====================================================

export async function requestAccountDeletion() {
    const {
        data: {
            session
        },
        error: sessionError
    } = await supabase.auth.getSession();

    if (sessionError) {
        throw sessionError;
    }

    if (!session?.user?.id) {
        throw new Error(
            "No active session found."
        );
    }

    const {
        data,
        error
    } = await supabase.functions.invoke(
        "delete-account",
        {
            body: {}
        }
    );

    if (error) {
        console.error(
            "Account deletion error:",
            error
        );

        throw error;
    }

    return data;
}