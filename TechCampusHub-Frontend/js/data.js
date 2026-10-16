
// =====================================================

// TechCampus_Hub

// data.js

// =====================================================


import { apiGet, getStoredUser, normalizeNote, normalizePyq, normalizeTnp, normalizeTimetable, normalizeUser } from "./api.js";

import { currentUserRole } from "./state.js";




// =====================================================

// Application Data

// =====================================================



export let notes = [];

export let tnpItems = [];

export let pyqItems = [];

export let timetableItems = [];

export let students = [];





// =====================================================

// Load Notes

// =====================================================

//

// Admins load through GET /api/admin/notes — the ADMIN-only list that

// returns every note (faculty uploads included) enriched with

// createdByName/createdByRole. Students and faculty keep the plain

// GET /api/notes payload, which never carries those fields.

//

// The role is resolved when loadNotes() runs, not at import time:

// currentUserRole is only populated inside initializeAuthenticatedUser,

// which runs after this module is already loaded. When state has not

// been set yet we fall back to the persisted profile so an admin is

// never dropped onto the student endpoint mid-session.

// =====================================================



function isAdminSession() {

    if (currentUserRole !== null && currentUserRole !== undefined) {

        return currentUserRole === "admin";

    }

    const stored = getStoredUser();

    return String(stored?.role || "").toLowerCase() === "admin";

}



export async function loadNotes() {

    try {

        const data = await apiGet(

            isAdminSession() ? "/api/admin/notes" : "/api/notes"

        );



        notes = (data || []).map(normalizeNote);

    } catch (error) {

        console.error(

            "Notes loading error:",

            error

        );



        notes = [];

    }



    return notes;

}






// =====================================================

// Load TNP

// =====================================================



export async function loadTnp() {

    try {

        const data = await apiGet("/api/tnp-notes");



        tnpItems = (data || []).map(normalizeTnp);

    } catch (error) {

        console.error(

            "TNP loading error:",

            error

        );



        tnpItems = [];

    }



    return tnpItems;

}






// =====================================================

// Load Previous Year Papers

// =====================================================



export async function loadPyq() {

    try {

        const data = await apiGet("/api/pyqs");



        pyqItems = (data || []).map(normalizePyq);

    } catch (error) {

        console.error(

            "PYQ loading error:",

            error

        );



        pyqItems = [];

    }



    return pyqItems;

}
// =====================================================

// Load Timetable

// =====================================================

export async function loadTimetable() {

    try {

        const data = await apiGet("/api/timetables");



        timetableItems = (data || []).map(normalizeTimetable);

    } catch (error) {

        console.error(

            "Timetable loading error:",

            error

        );



        timetableItems = [];

    }



    return timetableItems;

}

// =====================================================

// Load Students

// =====================================================

//

// Admin-only endpoint: the backend returns every profile

// (name, email, course, branch, year, semester, role).

// Passwords never leave the server.

// =====================================================



export async function loadStudents() {

    try {

        const data = await apiGet("/api/admin/users");



        students = (data || []).map(normalizeUser);

    } catch (error) {

        console.error(

            "Students loading error:",

            error

        );



        students = [];

    }



    return students;

}






// =====================================================

// Load All Data

// =====================================================



export async function loadAllData(

    includeStudents = false

) {

    const requests = [

        loadNotes(),

        loadTnp(),

        loadPyq(),

        loadTimetable()

    ];



    if (includeStudents) {

        requests.push(loadStudents());

    }



    await Promise.all(requests);



    return {

        notes,

        tnpItems,

        pyqItems,

        timetableItems,

        students

    };

}






// =====================================================

// Reload Students

// =====================================================



export async function reloadStudents() {

    return await loadStudents();

}






