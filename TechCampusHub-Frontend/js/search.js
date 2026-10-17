
// =====================================================

// TechCampus_Hub

// search.js

// =====================================================

// Data pipeline for the dashboard category views.

//

// The old client-side text/filter search (search bar, filter

// dropdowns, in-browser filtering) has been removed and replaced

// by the server-side Smart Search (see smartsearch.js), which

// searches inside the actual text of uploaded PDFs via the

// backend full-text index.

//

// The dashboard still browses one category at a time (Notes,

// TNP, PYQ, Exam Timetable) and the render layer asks this

// module for "the current list" of each category - which is

// simply the full loaded data now.

import {

    notes,

    tnpItems,

    pyqItems,

    timetableItems

} from "./data.js";


// --------------------------------------------------
// Category data pipelines (full lists - no filtering)
// --------------------------------------------------

export function getFilteredNotes() {
    return notes;
}

export function getFilteredTnp() {
    return tnpItems;
}

export function getFilteredPyq() {
    return pyqItems;
}

export function getFilteredTimetable() {
    return timetableItems;
}


// --------------------------------------------------
// Legacy hooks (kept as no-ops so existing call

// sites in ui.js / events.js keep working)
// --------------------------------------------------

export function updateSearchUI() {
}

export function updateAllSearchSubjects() {
}

export function populateSearchDropdowns() {
}

export async function refreshSearch() {
}
