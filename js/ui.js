


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

    signOut,

    requestAccountDeletion

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

export function updateStudentProfileUI(
    student = currentStudent
) {

    if (!student) {
        return;
    }

    const name =
        student.name ||
        "Student";

    const email =
        student.email ||
        "";

    const course =
        student.course ||
        "";

    const branch =
        student.branch ||
        "";

    const year =
        student.year ||
        "";

    const semester =
        student.semester ||
        "";


    // -------------------------------------------------
    // Header
    // -------------------------------------------------

    const userNameBadge =
        $("userNameBadge");

    if (userNameBadge) {

        userNameBadge.textContent =
            name;
    }


    // -------------------------------------------------
    // Profile Top
    // -------------------------------------------------

    const profileName =
        $("adminProfileName");

    const profileEmail =
        $("adminProfileEmail");

    const profileNameDetail =
        $("adminProfileNameDetail");

    if (profileName) {

        profileName.textContent =
            name;
    }

    if (profileEmail) {

        profileEmail.textContent =
            email;
    }

    if (profileNameDetail) {

        profileNameDetail.textContent =
            name;
    }


    // -------------------------------------------------
    // Student Profile Details
    // -------------------------------------------------

    const profileCourse =
        $("profileCourse");

    const profileBranch =
        $("profileBranch");

    const profileYear =
        $("profileYear");

    const profileSemester =
        $("profileSemester");


    // IMPORTANT:
    // Show student fields again after Admin login

    if (profileCourse) {

        profileCourse.closest("p")
            ?.classList.remove("hidden");

        profileCourse.textContent =
            course || "-";
    }


    if (profileBranch) {

        profileBranch.closest("p")
            ?.classList.remove("hidden");

        profileBranch.textContent =
            branch || "-";
    }


    if (profileYear) {

        profileYear.closest("p")
            ?.classList.remove("hidden");

        profileYear.textContent =
            year || "-";
    }


    if (profileSemester) {

        profileSemester.closest("p")
            ?.classList.remove("hidden");

        profileSemester.textContent =
            semester
                ? `Semester ${semester}`
                : "-";
    }


    // -------------------------------------------------
    // Role
    // -------------------------------------------------

    const adminRoleDetail =
        $("adminRoleDetail");

    if (adminRoleDetail) {

        adminRoleDetail.classList.add(
            "hidden"
        );
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
    // Profile Elements
    // -------------------------------------------------

    const userNameBadge =
        $("userNameBadge");

    const profileName =
        $("adminProfileName");

    const profileEmail =
        $("adminProfileEmail");

    const profileNameDetail =
        $("adminProfileNameDetail");

    const profileCourse =
        $("profileCourse");

    const profileBranch =
        $("profileBranch");

    const profileSemester =
        $("profileSemester");

    const studentProfileDetails =
        $("studentProfileDetails");

    const adminRoleDetail =
        $("adminRoleDetail");


    // -------------------------------------------------
    // Header
    // -------------------------------------------------

    if (userNameBadge) {

        userNameBadge.textContent =
            name;
    }


    // -------------------------------------------------
    // Profile Top
    // -------------------------------------------------

    if (profileName) {

        profileName.textContent =
            name;
    }


    if (profileEmail) {

        profileEmail.textContent =
            email;
    }


    // -------------------------------------------------
    // Name
    // -------------------------------------------------

    if (profileNameDetail) {

        profileNameDetail.textContent =
            name;
    }


    // -------------------------------------------------
    // Admin Role
    // -------------------------------------------------

    if (adminRoleDetail) {

        adminRoleDetail.classList.remove(
            "hidden"
        );
    }


    // -------------------------------------------------
    // Hide Student Details for Admin
    // -------------------------------------------------

if (profileCourse) {
    profileCourse.closest("p")
        ?.classList.add("hidden");
}

if (profileBranch) {
    profileBranch.closest("p")
        ?.classList.add("hidden");
}

if (profileYear) {
    profileYear.closest("p")
        ?.classList.add("hidden");
}

if (profileSemester) {
    profileSemester.closest("p")
        ?.classList.add("hidden");
}

if (adminRoleDetail) {
    adminRoleDetail.classList.remove("hidden");
}
}

// =====================================================

// Delete Profile Visibility

// =====================================================


export function updateDeleteProfileVisibility() {

    const button =

        $("deleteProfileBtn");



    if (!button) {

        return;

    }



    // Student only

    button.classList.toggle(

        "hidden",

        isAdminRole()

    );

}





// =====================================================

// Admin Login Button

// =====================================================



export function updateAdminLoginButton() {

    const button =

        $("adminLoginBtn");



    if (!button) {

        return;

    }



    button.classList.toggle(

        "hidden",

        !isAdminRole()

    );

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
// Profile Dropdown
// =====================================================

export function updateProfileDropdown() {
    const dropdown =
        $("adminProfileDropdown");
    if (!dropdown) {
        return;

    }
    const profileButton =

        $("profileButton");



    if (!profileButton) {

        return;

    }



    if (

        profileButton.dataset

            .dropdownReady === "true"

    ) {

        return;

    }



    profileButton.dataset

        .dropdownReady = "true";



    profileButton.addEventListener(

        "click",

        event => {

            event.stopPropagation();



            dropdown.classList.toggle(

                "hidden"

            );

        }

    );



    document.addEventListener(

        "click",

        event => {

            if (

                !dropdown.contains(

                    event.target

                ) &&

                !profileButton.contains(

                    event.target

                )

            ) {

                dropdown.classList.add(

                    "hidden"

                );

            }

        }

    );

}





// =====================================================

// Apply Role UI

// =====================================================



export function applyRoleUI() {

    if (isAdminRole()) {

        updateAdminProfileUI(

            null,

            currentStudent

        );



        showDashboard();

        showAdminPanel();



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
        const { supabase } =
            await import("../supabase.js");

        const {
            data,
            error
        } = await supabase
            .from("site_settings")
            .select("telegram_link")
            .eq("id", 1)
            .maybeSingle();

        if (error) {
            console.error(
                "Telegram link load error:",
                error
            );

            setTelegramLinkUI(
                DEFAULT_TELEGRAM_LINK
            );

            return DEFAULT_TELEGRAM_LINK;
        }

        const telegramLink =
            data?.telegram_link ||
            DEFAULT_TELEGRAM_LINK;

        setTelegramLinkUI(
            telegramLink
        );

        return telegramLink;

    } catch (error) {
        console.error(
            "Telegram link exception:",
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

    const { supabase } =
        await import("../supabase.js");

    const {
        data,
        error
    } = await supabase
        .from("site_settings")
        .update({
            telegram_link: link,
            updated_at:
                new Date().toISOString()
        })
        .eq("id", 1)
        .select("telegram_link")
        .single();

    if (error) {
        console.error(
            "Telegram link update error:",
            error
        );

        throw error;
    }

    const savedLink =
        data?.telegram_link || link;

    setTelegramLinkUI(
        savedLink
    );

    return savedLink;
}



// =====================================================
// Load Current Student Profile
// =====================================================



// IMPORTANT:
// Profile is fetched directly from Supabase instead
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

    } = await supabaseProfileQuery(

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



async function supabaseProfileQuery(

    userId

) {

    // Dynamic import avoids creating another permanent

    // dependency at module initialization.

    const {

        supabase

    } = await import("../supabase.js");



    return await supabase

        .from("students")

        .select(

            "id,email,name,course,branch,year,semester,role,created_at"

        )

        .eq("id", userId)

        .maybeSingle();

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

            : "student";



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



console.log("ROLE:", role);


// console.log("STUDENT:", student);



updateRegistrationFields();

updateNoteFields();

updatePyqFields();

updateTimetableFields();

updateSearchUI();

updateStudentFilters();

updateUploadPanel();


console.log("All dropdown update functions completed");



return {

    user: authUser,

    student,

    role

};

}





// =====================================================

// Auth Mode UI

// =====================================================



export function updateAuthModeUI() {



    console.log(

        "updateAuthModeUI called, getSignupMode() =",

        getSignupMode()

    );



    const heading =

        $("authHeading");



    const submitButton =

        $("authSubmitBtn");



    const signupFields =

        $("signupFields");



    const forgotPassword =

        $("forgotPasswordContainer");



    const toggleButton =

        $("toggleAuthMode");

        

if (getSignupMode()){

        if (heading) {

            heading.textContent =

                "Create Student Account";

        }



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

        if (heading) {

            heading.textContent =

                "Welcome Back";

        }



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

                "Create a new student account";

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

    showAuthSection();



    updateAuthModeUI();



    initializeRegistrationFields();



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

// Refresh Dashboard

// =====================================================



export async function refreshDashboard() {

    const result =

        await initializeAuthenticatedUser();



    if (!result) {

        return null;

    }



    applyRoleUI();



    populateSearchDropdowns();



    console.log("ROLE:", result.role);



    const category =

        $("searchCategory")?.value ||

        "notes";



    await renderCurrentCategory(

        category

    );



    return result;

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

        resetAuthForm();
        setSignupMode(false);
        updateAuthModeUI();

        const dropdown = $("adminProfileDropdown");
        if (dropdown) {
            dropdown.classList.add("hidden");
        }

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
// Delete Profile Modal
// =====================================================



export function openDeleteProfileModal() {

    if (isAdminRole()) {

        return;

    }



    const modal =

        $("deleteProfileModal");



    if (!modal) {

        return;

    }



    const message =

        $("deleteProfileMessage");



    if (message) {

        message.textContent =

            "Are you sure you want to delete your profile?";

    }



    modal.classList.remove(

        "hidden"

    );

}





export function closeDeleteProfileModal() {

    const modal =

        $("deleteProfileModal");



    if (!modal) {

        return;

    }



    modal.classList.add(

        "hidden"

    );

}





// =====================================================

// Confirm Delete Profile

// =====================================================



export async function confirmDeleteProfile() {

    if (isAdminRole()) {

        return false;

    }



    const confirmButton =

        $("confirmDeleteProfileBtn");



    if (confirmButton) {

        confirmButton.disabled =

            true;



        confirmButton.textContent =

            "Deleting...";

    }



    try {

        await requestAccountDeletion();



        clearUserState();



        closeDeleteProfileModal();



        hideElement(

            $("dashboardSection")

        );



        hideElement(

            $("adminPanel")

        );



        showAuthSection();



        setSignupMode(

            true

        );



        resetAuthForm();



        updateAuthModeUI();



        showAuthMessage(

            "Your profile has been deleted. Please create a new account.",

            "success"

        );



        return true;



    } catch (error) {

        console.error(

            "Profile deletion error:",

            error

        );



        showAuthMessage(

            error.message ||

                "Unable to delete your profile.",

            "error"

        );



        return false;



    } finally {

        if (confirmButton) {

            confirmButton.disabled =

                false;



            confirmButton.textContent =

                "Delete";

        }

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

// =====================================================
// Initialize Profile UI
// =====================================================

export function initializeProfileUI() {

    updateProfileDropdown();

    updateDeleteProfileVisibility();

}

// =====================================================
// Compatibility Export
// =====================================================

export function updateDeleteProfileButton() {

    updateDeleteProfileVisibility();

}



