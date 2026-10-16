


// =====================================================

// TechCampus_Hub

// ui.js

// =====================================================



import {

    $,

    showElement,

    hideElement,

    setSelectValue,

    populateCourses,

    populateBranches,

    populateYears,

    populateSemesters,

    updateRegistrationFields,

    updateNoteFields,

    updatePyqFields,

    updateTimetableFields

} from "./helpers.js";



import {

    getSession,

    getCurrentUser,

    signOut

} from "./auth.js";



import {

    loadAllData

} from "./data.js";



import {

    ADMIN_EMAIL

} from "./config.js";



import {

    getSignupMode,

    setSignupMode,

    getSignupRole,

    currentStudent,

    currentUserRole,

    setCurrentStudent,

    setCurrentUserRole,

    clearUserState

} from "./state.js";



import {

    updateSearchUI,

    updateAllSearchSubjects,

    populateSearchDropdowns

} from "./search.js";



import {

    renderCurrentCategory

} from "./render.js";





// =====================================================

// Auth Message

// =====================================================



export function showAuthMessage(

    message,

    type = "error"

) {

    const element =

        $("authMessage");



    if (!element) {

        return;

    }



    element.textContent =

        String(message || "");



    element.className =

        "mt-4 rounded-xl px-4 py-3 text-sm";



    if (type === "success") {

        element.classList.add(

            "bg-green-50",

            "border",

            "border-green-200",

            "text-green-700"

        );

    } else {

        element.classList.add(

            "bg-red-50",

            "border",

            "border-red-200",

            "text-red-700"

        );

    }



    element.classList.remove(

        "hidden"

    );

}





// =====================================================

// Reset Auth Form

// =====================================================



export function resetAuthForm() {

    const form =

        $("authForm");



    if (form) {

        form.reset();

    }



    const message =

        $("authMessage");



    if (message) {

        message.textContent = "";



        message.classList.add(

            "hidden"

        );

    }

}





// =====================================================

// Show Auth Section

// =====================================================



// Current route: "auth", "dashboard", "notifications", ...
function currentPage() {
    return document.body?.dataset?.page || "auth";
}

export function showAuthSection() {

    showElement(

        $("authSection")

    );



    hideElement(

        $("dashboardSection")

    );



    hideElement(

        $("adminPanel")

    );

}





// =====================================================

// Show Dashboard

// =====================================================



export function showDashboard() {

    hideElement(

        $("authSection")

    );



    showElement(

        $("dashboardSection")

    );

}





// =====================================================

// Show Admin Panel

// =====================================================



export function showAdminPanel() {

    if (!isAdminRole()) {

        hideElement(

            $("adminPanel")

        );



        return;

    }



    showElement(

        $("adminPanel")

    );

}





// =====================================================

// Hide Admin Panel

// =====================================================



export function hideAdminPanel() {

    hideElement(

        $("adminPanel")

    );

}





// =====================================================

// Admin Role

// =====================================================



function isAdminRole() {

    return currentUserRole === "admin";

}

// =====================================================
// Show Search / Learning Resources only for Students
// =====================================================

function updateSearchVisibility() {

    const searchSection = $("searchFiltersSection");

    if (!searchSection) {
        return;
    }

    if (isAdminRole()) {
        hideElement(searchSection);
    } else {
        showElement(searchSection);
    }

}



// =====================================================

// Update Admin Upload UI

// =====================================================



export function updateAdminUploadUI() {

    const adminPanel =

        $("adminPanel");



    if (!adminPanel) {

        return;

    }



    if (!isAdminRole()) {

        hideElement(

            adminPanel

        );



        return;

    }



    showElement(

        adminPanel

    );



    const typeSelect =

        $("adminUploadType");



    if (!typeSelect) {

        return;

    }



    const type =

        typeSelect.value || "notes";



    const forms = {

        notes: $("uploadNotesForm"),

        tnp: $("uploadTnpForm"),

        pyq: $("uploadPyqForm"),

        timetable:

            $("uploadTimetableForm")

    };



    Object.entries(forms).forEach(

        ([name, form]) => {

            if (!form) {

                return;

            }



            form.classList.toggle(

                "hidden",

                name !== type

            );

        }

    );

}


// =====================================================
// Update Student Profile UI
// =====================================================

//
// Small helper: writes text into an element by id
// when the element exists on the current page.
//

function setTextContent(id, value) {
    const element = $(id);

    if (element) {
        element.textContent = value;
    }
}

//
// Profile rows that are shown for students and
// hidden for administrators in the sidebar.
//

const SIDEBAR_STUDENT_ROWS = [
    "sidebarProfileEnrollmentRow",
    "sidebarProfileBranchRow",
    "sidebarProfileSemesterRow",
    "sidebarProfilePhoneRow"
];

//
// Sidebar profile rows that faculty accounts do not
// have (no enrollment number, branch or semester).
//

const SIDEBAR_FACULTY_HIDDEN_ROWS = [
    "sidebarProfileEnrollmentRow",
    "sidebarProfileBranchRow",
    "sidebarProfileSemesterRow"
];

export function updateStudentProfileUI(
    student = currentStudent
) {

    if (!student) {
        return;
    }

    const isFaculty =
        currentUserRole === "faculty" ||
        String(student.role || "").toLowerCase() === "faculty";

    const name =
        student.name ||
        (isFaculty ? "Faculty" : "Student");

    const email =
        student.email ||
        "";

    const course =
        student.course ||
        "";

    const branch =
        student.branch ||
        "";

    const semester =
        student.semester ||
        "";

    const phone =
        student.phone ||
        "";

    const enrollment =
        student.id ?? "";


    // -------------------------------------------------
    // Sidebar Profile Section
    // -------------------------------------------------

    setTextContent(
        "sidebarProfileName",
        name
    );

    // Sidebar heading text ("Student Profile" / "Faculty Profile").
    setTextContent(
        "sidebarProfileTitle",
        isFaculty ? "Faculty Profile" : "Student Profile"
    );

    setTextContent(
        "sidebarProfileEnrollment",
        enrollment || "-"
    );

    setTextContent(
        "sidebarProfileEmail",
        email || "-"
    );

    setTextContent(
        "sidebarProfileBranch",
        branch || "-"
    );

    setTextContent(
        "sidebarProfileSemester",
        semester
            ? `Semester ${semester}`
            : "-"
    );

    setTextContent(
        "sidebarProfilePhone",
        phone || "-"
    );

    // Students see every profile row.
    SIDEBAR_STUDENT_ROWS.forEach(
        rowId => $(rowId)?.classList.remove("hidden")
    );

    // Faculty keep name/email/phone only.
    if (isFaculty) {
        SIDEBAR_FACULTY_HIDDEN_ROWS.forEach(
            rowId => $(rowId)?.classList.add("hidden")
        );
    }


    // -------------------------------------------------
    // -------------------------------------------------
    // Profile Page Section
    // -------------------------------------------------

    const pageName = $("studentProfilePageName");

    const pageEmail = $("studentProfilePageEmail");

    const pageCourse = $("studentProfilePageCourse");

    const pageSemester = $("studentProfilePageSemester");

    if (pageName) {
        pageName.textContent = name;
    }

    if (pageEmail) {
        pageEmail.textContent = email || "-";
    }

    if (pageCourse) {
        pageCourse.textContent = course || "-";
    }

    if (pageSemester) {
        pageSemester.textContent = semester
            ? `Semester ${semester}`
            : "-";
    }
}

// =====================================================
// Update Admin Profile UI
// =====================================================

export function updateAdminProfileUI(
    user = null,
    student = null
) {

    const name = "Administrator";

    const email =
        user?.email ||
        student?.email ||
        ADMIN_EMAIL;


    // -------------------------------------------------
    // Sidebar Profile Section
    // -------------------------------------------------

    setTextContent(
        "sidebarProfileName",
        name
    );

    setTextContent(
        "sidebarProfileEmail",
        email || "-"
    );

    // Admins have no student academic details.
    SIDEBAR_STUDENT_ROWS.forEach(
        rowId => $(rowId)?.classList.add("hidden")
    );
}

// =====================================================

// Admin nav visibility

// =====================================================



export function updateAdminNavVisibility() {
    const navLink = $("adminNavBtn");

    if (!navLink) {
        return;
    }

    navLink.classList.toggle(
        "hidden",
        !isAdminRole()
    );
}





// =====================================================

// Faculty nav visibility

// =====================================================



//
// Faculty get their own workspace entry; the regular
// student dashboard links are removed while a faculty
// account is signed in and restored for everyone else.
//
// Every lookup is null-guarded because these ids only
// exist on pages that render the shared sidebar.
//

export function updateFacultyNavVisibility() {
    const isFaculty = currentUserRole === "faculty";

    const facultyNavBtn = $("facultyNavBtn");

    if (facultyNavBtn) {
        facultyNavBtn.classList.toggle(
            "hidden",
            !isFaculty
        );
    }

    if (isFaculty) {
        [
            "dashboardHomeBtn",
            "favoritesBtn",
            "coursesBtn",
            "paymentHistoryBtn",
            "certificatesBtn",
            "feedbackBtn"
        ].forEach(id => $(id)?.classList.add("hidden"));

        return;
    }

    // Students and administrators keep the regular links.
    [
        "dashboardHomeBtn",
        "favoritesBtn",
        "coursesBtn",
        "paymentHistoryBtn",
        "certificatesBtn",
        "feedbackBtn"
    ].forEach(id => $(id)?.classList.remove("hidden"));
}





// =====================================================

// Update Student Filters

// =====================================================



export function updateStudentFilters() {

    if (isAdminRole()) {

        return;

    }



    const student =

        currentStudent;



    if (!student) {

        return;

    }



    // -------------------------------------------------

    // Notes

    // -------------------------------------------------



    setSelectValue(

        $("searchCourse"),

        student.course

    );



    setSelectValue(

        $("searchBranch"),

        student.branch

    );



    // Student Year is NOT selected/used.

    const searchYear =

        $("searchYear");



    if (searchYear) {

        searchYear.value = "";

    }



    // -------------------------------------------------

    // PYQ

    // -------------------------------------------------



    setSelectValue(

        $("pyqSearchCourse"),

        student.course

    );



    const pyqYear =

        $("pyqSearchYear");



    if (pyqYear) {

        pyqYear.value = "";

    }



    // -------------------------------------------------

    // Timetable

    // -------------------------------------------------



    setSelectValue(

        $("timetableSearchCourse"),

        student.course

    );

}





// =====================================================

// Update Student Profile Filter Options

// =====================================================



function populateStudentProfileFilters() {

    const student =

        currentStudent;



    if (!student) {

        return;

    }



    // -------------------------------------------------

    // Notes

    // -------------------------------------------------



    const searchCourse =

        $("searchCourse");



    const searchBranch =

        $("searchBranch");



    if (searchCourse) {

        searchCourse.value =

            student.course;

    }



    if (searchBranch) {

        searchBranch.value =

            student.branch;

    }



    // -------------------------------------------------

    // PYQ

    // -------------------------------------------------



    const pyqCourse =

        $("pyqSearchCourse");



    if (pyqCourse) {

        pyqCourse.value =

            student.course;

    }



    // -------------------------------------------------

    // Timetable

    // -------------------------------------------------



    const timetableCourse =

        $("timetableSearchCourse");



    if (timetableCourse) {

        timetableCourse.value =

            student.course;

    }

}





// =====================================================

// Update Role Based Filter UI

// =====================================================



export function updateStudentRoleUI() {

    const student =

        currentStudent;



    const admin =

        isAdminRole();



    // -------------------------------------------------

    // Notes

    // -------------------------------------------------



    const notesCourse =

        $("searchCourse");



    const notesBranch =

        $("searchBranch");



    const notesYear =

        $("searchYear");



    if (notesCourse) {

        notesCourse.disabled =

            !admin;

    }



    if (notesBranch) {

        notesBranch.disabled =

            !admin;

    }



    if (notesYear) {

        notesYear.disabled =

            !admin;

    }



    // -------------------------------------------------

    // PYQ

    // -------------------------------------------------



    const pyqCourse =

        $("pyqSearchCourse");



    const pyqYear =

        $("pyqSearchYear");



    if (pyqCourse) {

        pyqCourse.disabled =

            !admin;

    }



    if (pyqYear) {

        pyqYear.disabled =

            !admin;

    }



    // -------------------------------------------------

    // Timetable

    // -------------------------------------------------



    const timetableCourse =

        $("timetableSearchCourse");



    if (timetableCourse) {

        timetableCourse.disabled =

            !admin;

    }



    // -------------------------------------------------

    // Student automatic values

    // -------------------------------------------------



    if (!admin && student) {

        populateStudentProfileFilters();

    }

}

// =====================================================
// Update Upload Panel
// =====================================================

export function updateUploadPanel() {
    if (isAdminRole()) {
        updateAdminUploadUI();
    } else {
        hideAdminPanel();
    }
}

// =====================================================

// Apply Role UI

// =====================================================



export function applyRoleUI() {
    const page = currentPage();

    const admin = isAdminRole();

    const faculty = currentUserRole === "faculty";

    // Login page: after a successful login go to the right home.
    if (page === "auth") {
        if (admin) {
            window.location.replace("./admin.html");
        } else if (faculty) {
            window.location.replace("./faculty.html");
        } else {
            window.location.replace("./dashboard.html");
        }
        return;
    }

    // The admin area is only for admins.
    if (page === "admin" && !admin) {
        window.location.replace(
            faculty ? "./faculty.html" : "./dashboard.html"
        );
        return;
    }

    // The faculty area is only for faculty.
    if (page === "faculty" && !faculty) {
        window.location.replace(
            admin ? "./admin.html" : "./dashboard.html"
        );
        return;
    }

    // Faculty always work inside their dedicated dashboard.
    if (page === "dashboard" && faculty) {
        window.location.replace("./faculty.html");
        return;
    }


    if (admin) {

        updateAdminProfileUI(

            null,

            currentStudent

        );



        showDashboard();

        showAdminPanel();



    } else if (faculty) {

        // Faculty share the student shell for the shared UI
        // but keep their own navigation set.
        updateStudentProfileUI(

            currentStudent

        );

        showDashboard();

        hideAdminPanel();

        updateFacultyNavVisibility();

    } else {

        updateStudentProfileUI(

            currentStudent

        );

        showDashboard();

        hideAdminPanel();

    }

updateStudentFilters();
updateStudentRoleUI();
updateUploadPanel();

updateSearchVisibility();

updateSearchUI();
updateAllSearchSubjects();

    updateAdminNavVisibility();

    // Restore/hide the faculty entry for every role so the
    // toggles always end in a consistent state.
    updateFacultyNavVisibility();
}




// =====================================================
// TELEGRAM COMMUNITY LINK
// =====================================================

const DEFAULT_TELEGRAM_LINK =
    "https://t.me/TechCampus_Hub";

// =====================================================
// Load Telegram Link
// =====================================================

export async function loadTelegramLink() {
    try {
        const { apiGet, normalizeSettings } =
            await import("./api.js");

        const data = await apiGet(
            "/api/settings",
            false
        );

        const settings = normalizeSettings(data);

        const telegramLink =
            settings?.telegram_link ||
            DEFAULT_TELEGRAM_LINK;

        setTelegramLinkUI(
            telegramLink
        );

        return telegramLink;

    } catch (error) {
        console.error(
            "Telegram link load error:",
            error
        );

        setTelegramLinkUI(
            DEFAULT_TELEGRAM_LINK
        );

        return DEFAULT_TELEGRAM_LINK;
    }
}

// =====================================================
// Update Telegram Link UI
// =====================================================

function setTelegramLinkUI(link) {
    const joinLink =
        document.getElementById(
            "telegramJoinLink"
        );

    const input =
        document.getElementById(
            "telegramLinkInput"
        );

    if (joinLink) {
        joinLink.href = link;
    }

    if (input) {
        input.value = link;
    }
}

// =====================================================
// Validate Telegram URL
// =====================================================

function isValidTelegramLink(value) {
    try {
        const url =
            new URL(value);

        const validHost =
            url.hostname === "t.me" ||
            url.hostname === "telegram.me";

        return (
            url.protocol === "https:" &&
            validHost &&
            url.pathname.length > 1
        );

    } catch {
        return false;
    }
}

// =====================================================
// Update Telegram Link
// =====================================================

export async function updateTelegramLink(
    newLink
) {
    const link =
        String(newLink || "").trim();

    if (!isValidTelegramLink(link)) {
        throw new Error(
            "Please enter a valid Telegram link, for example: https://t.me/TechCampus_Hub"
        );
    }

    const { apiPut, normalizeSettings } =
        await import("./api.js");

    const data = await apiPut(
        "/api/settings",
        { telegramLink: link }
    );

    const settings = normalizeSettings(data);

    const savedLink =
        settings?.telegram_link || link;

    setTelegramLinkUI(
        savedLink
    );

    return savedLink;
}



// =====================================================
// Load Current Student Profile
// =====================================================



// IMPORTANT:
// Profile is fetched from the backend instead
// of relying only on cached students[] data.
// =====================================================


export async function loadCurrentStudent(

    userId = null

) {

    const targetUserId =

        userId;



    if (!targetUserId) {

        setCurrentStudent(null);

        return null;

    }



    const {

        data,

        error

    } = await profileQuery(

        targetUserId

    );



    if (error) {

        console.error(

            "Student profile loading error:",

            error

        );



        setCurrentStudent(null);



        return null;

    }



    setCurrentStudent(

        data || null

    );



    return data || null;

}





// =====================================================

// Direct Student Profile Query

// =====================================================

async function profileQuery(

    userId

) {

    // Dynamic import avoids creating another permanent
    // dependency at module initialization.
    //
    // The backend derives the caller from the JWT, so the
    // profile always belongs to the signed-in user (the only
    // user loadCurrentStudent is ever asked about).

    const {

        apiGet,

        normalizeUser

    } = await import("./api.js");



    try {

        const data = await apiGet("/api/auth/me");



        return {

            data: normalizeUser(data),

            error: null

        };

    } catch (error) {

        return {

            data: null,

            error

        };

    }

}





// =====================================================

// Initialize Authenticated User

// =====================================================



export async function initializeAuthenticatedUser(user = null) {

    let authUser =

        user;



    // -------------------------------------------------

    // Get current user if not supplied

    // -------------------------------------------------



    if (!authUser) {

        const result =

            await getCurrentUser();



        if (result.error) {

            console.error(

                "Current user error:",

                result.error

            );



            clearUserState();



            showAuthSection();



            return null;

        }



        authUser =

            result.user;

    }



    if (!authUser) {

        clearUserState();



        showAuthSection();



        return null;

    }



    // -------------------------------------------------

    // Load DB profile

    // -------------------------------------------------



    const student =

        await loadCurrentStudent(

            authUser.id

        );



    if (!student) {

        console.error(

            "Authenticated user profile not found."

        );



        clearUserState();



        await signOut();



        showAuthSection();



        showAuthMessage(

            "Your profile could not be loaded. Please sign up again.",

            "error"

        );



        return null;

    }



    // -------------------------------------------------

    // Role comes from DB profile.

    //

    // Email is NOT the security authority.

    // -------------------------------------------------



    const role =

        student.role === "admin"

            ? "admin"

            : (student.role === "faculty" ? "faculty" : "student");



    setCurrentUserRole(

        role

    );



    // -------------------------------------------------

    // Load application data

    // -------------------------------------------------



    await loadAllData(

        role === "admin"

    );



    // -------------------------------------------------

// Apply UI

// -------------------------------------------------



applyRoleUI();





// =====================================================

// Populate all dynamic dropdowns

// =====================================================



populateCourses($("regCourse"));



if (role === "admin") {

    // Admin Upload Forms

    populateCourses($("noteCourse"));

    populateCourses($("pyqCourse"));

    populateCourses($("timetableCourse"));

}



// Search Filters

populateCourses($("searchCourse"));

populateCourses($("pyqSearchCourse"));

populateCourses($("timetableSearchCourse"));



// Dependent dropdowns

updateRegistrationFields();

updateNoteFields();

updatePyqFields();

updateTimetableFields();



// =====================================================

// Initialize Search Dropdowns

// =====================================================



populateSearchDropdowns();



updateRegistrationFields();

updateNoteFields();

updatePyqFields();

updateTimetableFields();

updateSearchUI();

updateStudentFilters();

updateUploadPanel();



return {

    user: authUser,

    student,

    role

};

}





// =====================================================
// SIGNUP ROLE UI
// Shared by updateAuthRoleUI (events.js) and
// updateAuthModeUI below so both always agree on the
// heading and on which signup field group is visible.
// =====================================================

//
// Segmented Student/Faculty tab state:
// active tab = solid blue, inactive = slate text.
//

function setRoleTabState(button, active) {
    if (!button) {
        return;
    }

    button.classList.toggle("bg-blue-600", active);
    button.classList.toggle("text-white", active);
    button.classList.toggle("text-slate-600", !active);
    button.classList.toggle("hover:text-slate-900", !active);

    button.setAttribute(
        "aria-selected",
        active ? "true" : "false"
    );
}

//
// Everything that depends on the chosen signup role:
// heading, subtitle, the Student/Faculty field groups
// and the role tab active states. Safe to call in
// login mode (renders the student defaults).
//

export function syncSignupRoleUI() {
    const signup = getSignupMode();

    const isFaculty = signup && getSignupRole() === "faculty";

    const heading = $("authHeading");

    if (heading) {
        heading.textContent = signup
            ? (isFaculty
                ? "Create Faculty Account"
                : "Create Student Account")
            : "Welcome Back";
    }

    const authSubtitle = $("authSubtitle");

    if (authSubtitle) {
        authSubtitle.textContent = signup
            ? (isFaculty
                ? "Register as faculty to publish courses and upload notes."
                : "Create your account to unlock notes, papers and timetables.")
            : "Sign in to access your personalized learning resources.";
    }

    // -------------------------------------------------
    // Student / Faculty field groups (inside #signupFields)
    // -------------------------------------------------

    const studentSignupFields = $("studentSignupFields");

    const facultySignupFields = $("facultySignupFields");

    if (isFaculty) {
        showElement(facultySignupFields);
        hideElement(studentSignupFields);
    } else {
        showElement(studentSignupFields);
        hideElement(facultySignupFields);
    }

    // -------------------------------------------------
    // Segmented role tabs
    // -------------------------------------------------

    setRoleTabState($("roleTabStudent"), !isFaculty);
    setRoleTabState($("roleTabFaculty"), isFaculty);
}





// =====================================================

// Auth Mode UI

// =====================================================



export function updateAuthModeUI() {

    // Heading, subtitle and Student/Faculty field groups.
    syncSignupRoleUI();

    const submitButton =

        $("authSubmitBtn");



    const signupFields =

        $("signupFields");



    const forgotPassword =

        $("forgotPasswordContainer");



    const toggleButton =

        $("toggleAuthMode");

        

if (getSignupMode()){

        if (submitButton) {

            submitButton.textContent =

                "Create Account";

        }



        showElement(

            signupFields

        );



        hideElement(

            forgotPassword

        );



        if (toggleButton) {

            toggleButton.textContent =

                "Already have an account? Login";

        }



    } else {

        if (submitButton) {

            submitButton.textContent =

                "Login";

        }



        hideElement(

            signupFields

        );



        showElement(

            forgotPassword

        );



        if (toggleButton) {

            toggleButton.textContent =

                "Don't have an account? Sign up";

        }

    }

}





// =====================================================

// Initialize Registration Fields

// =====================================================



function initializeRegistrationFields() {

    const courseSelect =

        $("regCourse");



    const branchSelect =

        $("regBranch");



    const yearSelect =

        $("regYear");



    const semesterSelect =

        $("regSemester");



    if (!courseSelect) {

        return;

    }



    const course =

        courseSelect.value;



    if (branchSelect) {

        populateBranches(

            courseSelect,

            branchSelect

        );

    }



    if (yearSelect) {

        populateYears(

            yearSelect,

            course

        );

    }



    if (semesterSelect) {

        populateSemesters(

            semesterSelect,

            course

        );

    }

}





// =====================================================

// Initialize Application

// =====================================================



export async function initializeApp() {

    // The auth screen only exists on the login page.
    if (currentPage() === "auth") {

        showAuthSection();

        updateAuthModeUI();

        initializeRegistrationFields();

        // Arrived here after deleting the profile: confirm it worked.
        if (new URLSearchParams(window.location.search).get("deleted") === "1") {

            showAuthMessage(

                "Your profile has been deleted. You can create a new account.",

                "success"

            );

        }

    }



    const {

        data,

        error

    } = await getSession();



    if (error) {

        console.error(

            "Session loading error:",

            error

        );



        clearUserState();



        return null;

    }



    const session =

        data?.session;



    if (!session?.user) {

        clearUserState();



        return null;

    }



    return await initializeAuthenticatedUser(

        session.user

    );

}





// =====================================================
// Logout
// =====================================================

export async function logoutUser() {
    try {
        const success = await signOut();

        // Local state clear karo
        clearUserState();

        hideElement($("dashboardSection"));
        hideElement($("adminPanel"));
        showAuthSection();

        // Logging out returns the user to the Home page;
        // logging in again happens via the Login page.
        if (currentPage() !== "auth" && currentPage() !== "home") {
            window.location.replace("./index.html");
            return;
        }

        resetAuthForm();
        setSignupMode(false);
        updateAuthModeUI();

        return success;

    } catch (error) {
        console.error("Logout error:", error);

        // UI ko bhi logout state mein rakho
        clearUserState();
        hideElement($("dashboardSection"));
        hideElement($("adminPanel"));
        showAuthSection();

        return false;
    }
}

// =====================================================

// Category Change

// =====================================================



export async function handleCategoryChange(

    category

) {

    const searchCategory =

        $("searchCategory");



    if (searchCategory) {

        searchCategory.value =

            category;

    }



    updateSearchUI();

    updateAllSearchSubjects();



    await renderCurrentCategory(

        category

    );

}

