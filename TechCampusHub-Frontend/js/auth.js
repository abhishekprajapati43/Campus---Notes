// =====================================================
// TechCampus_Hub
// auth.js
//
// REST authentication against the Spring Boot backend.
//
// The public contract of this module is unchanged from
// the Supabase version so no caller had to be rewritten:
//   signUpStudent(...)  -> { data: { user, session }, error }
//   signIn(...)         -> { data: { user, session }, error }
//   resetPassword(...)  -> { error }
//   getSession()        -> { data: { session }, error }
//   getCurrentUser()    -> { user, error }
//   signOut()           -> boolean
//   onAuthStateChange(cb) — cb(event, session)
//
// The JWT lives in localStorage (js/api.js) and is sent
// as "Authorization: Bearer <token>".
// =====================================================

import {
    apiPost,
    getAuthToken,
    getStoredUser,
    setAuthToken,
    setStoredUser,
    clearAuth,
    normalizeUser
} from "./api.js";

// =====================================================
// Auth-state listeners (SIGNED_IN / SIGNED_OUT)
// =====================================================

const listeners = [];

function emitAuthState(event, session) {
    listeners.forEach(callback => {
        try {
            callback(event, session);
        } catch (error) {
            console.error("Auth state listener error:", error);
        }
    });
}

/** Build the Supabase-shaped session object callers expect. */
function buildSession(user) {
    return user ? { user } : null;
}

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
    try {
        const data = await apiPost(
            "/api/auth/register",
            {
                name: String(name || "").trim(),
                email: String(email || "").trim(),
                password,
                course: String(course || "").trim(),
                branch: String(branch || "").trim(),
                year: Number(year) || null,
                semester: Number(semester) || null
            },
            false
        );

        const user = normalizeUser(data?.user);

        setAuthToken(data?.token || null);
        setStoredUser(user);

        emitAuthState("SIGNED_IN", buildSession(user));

        return {
            data: {
                user,
                session: buildSession(user)
            },
            error: null
        };
    } catch (error) {
        console.error("Sign up error:", error);

        return {
            data: { user: null, session: null },
            error
        };
    }
}

// =====================================================
// Sign In
// =====================================================

export async function signIn(email, password) {
    try {
        const data = await apiPost(
            "/api/auth/login",
            {
                email: String(email || "").trim(),
                password
            },
            false
        );

        const user = normalizeUser(data?.user);

        setAuthToken(data?.token || null);
        setStoredUser(user);

        emitAuthState("SIGNED_IN", buildSession(user));

        return {
            data: {
                user,
                session: buildSession(user)
            },
            error: null
        };
    } catch (error) {
        console.error("Sign in error:", error);

        return {
            data: { user: null, session: null },
            error
        };
    }
}

// =====================================================
// Reset Password
// =====================================================

export async function resetPassword(email) {
    try {
        await apiPost(
            "/api/auth/forgot-password",
            { email: String(email || "").trim() },
            false
        );

        return { error: null };
    } catch (error) {
        console.error("Password reset error:", error);

        return { error };
    }
}

// =====================================================
// Get Current Session
// =====================================================

export async function getSession() {
    try {
        const token = getAuthToken();

        if (!token) {
            return {
                data: { session: null },
                error: null
            };
        }

        const user = getStoredUser();

        return {
            data: { session: buildSession(user) },
            error: null
        };
    } catch (error) {
        return {
            data: { session: null },
            error
        };
    }
}

// =====================================================
// Get Current Auth User
// =====================================================

export async function getCurrentUser() {
    try {
        if (!getAuthToken()) {
            return { user: null, error: null };
        }

        // Refresh the cached profile from the backend.
        const { apiGet } = await import("./api.js");

        const fresh = await apiGet("/api/auth/me");

        const user = normalizeUser(fresh);

        setStoredUser(user);

        return { user, error: null };
    } catch (error) {
        // An expired/invalid token means "signed out".
        if (error.status === 401 || error.status === 403) {
            clearAuth();

            return { user: null, error: null };
        }

        console.error("Current user error:", error);

        return {
            user: getStoredUser(),
            error
        };
    }
}

// =====================================================
// Sign Out
// =====================================================

export async function signOut() {
    try {
        clearAuth();

        emitAuthState("SIGNED_OUT", null);

        return true;
    } catch (error) {
        console.error("Logout exception:", error);

        return false;
    }
}

// =====================================================
// Auth State Listener
// =====================================================

export function onAuthStateChange(callback) {
    if (typeof callback === "function") {
        listeners.push(callback);
    }

    return {
        data: {
            subscription: { unsubscribe() {} }
        },
        error: null
    };
}

// =====================================================
// Delete Current Student Account
// =====================================================

export async function requestAccountDeletion() {
    const {
        data: { session }
    } = await getSession();

    if (!session?.user?.id) {
        throw new Error("No active session found.");
    }

    const { apiDelete } = await import("./api.js");

    const data = await apiDelete("/api/account");

    clearAuth();

    emitAuthState("SIGNED_OUT", null);

    return data ?? { success: true };
}
