
// =====================================================
// TechCampus_Hub
// events.js
// =====================================================

import {
    $,
    updateRegistrationFields,
    updateNoteFields,
    updatePyqFields,
    updateTimetableFields
} from "./helpers.js";

import {
    setSignupMode,
    isSignupMode,
    currentUserRole
} from "./state.js";

import {
    signIn,
    signUpStudent,
    resetPassword,
    onAuthStateChange,
    signOut
} from "./auth.js";

import {
    initializeAuthenticatedUser,
    updateAuthModeUI,
    showAuthMessage,
    resetAuthForm,
    logoutUser,
    openDeleteProfileModal,
    closeDeleteProfileModal,
    confirmDeleteProfile,
    handleCategoryChange,
    updateUploadPanel,
    updateStudentFilters,
    loadTelegramLink,
    updateTelegramLink
} from "./ui.js";

import {
    uploadNotes,
    uploadTnp,
    uploadPyq,
    uploadTimetable,
    setupAdminDynamicActions,
    editRecord
} from "./admin.js";

import {
    updateSearchUI,
    refreshSearch
} from "./search.js";

import {
    renderCurrentCategory,
    setupRenderActions
} from "./render.js";

import {
    loadAllData
} from "./data.js";

// =====================================================
// Prevent duplicate setup
// =====================================================

let eventsInitialized = false;
let authListenerInitialized = false;

// =====================================================
// Admin Login State
// =====================================================

let adminLoginIdentifier = "";

// =====================================================
// Utility
// =====================================================

function isAdmin() {
    return currentUserRole === "admin";
}

function getErrorMessage(error) {
    return (
        error?.message ||
        "Something went wrong. Please try again."
    );
}

// =====================================================
// AUTH UI
// Main login is ALWAYS Student login.
// Administrator login is handled through the
// separate admin modal.
// =====================================================

function updateAuthRoleUI() {
    const authHeading = $("authHeading");
    const authMessage = $("authMessage");
    const signupFields = $("signupFields");
    const authToggleText = $("authToggleText");
    const toggleAuthMode = $("toggleAuthMode");
    const authSubmitBtn = $("authSubmitBtn");
    const forgotPasswordContainer = $("forgotPasswordContainer");

    // -------------------------------------------------
    // Main auth screen = Student
    // -------------------------------------------------

    if (authHeading) {
        authHeading.textContent = isSignupMode
            ? "Create Student Account"
            : "Welcome Back";
    }

    if (signupFields) {
        signupFields.classList.toggle(
            "hidden",
            !isSignupMode
        );
    }

    if (authToggleText) {
        authToggleText.textContent = isSignupMode
            ? "Already have an account?"
            : "Don't have an account?";
    }

    if (toggleAuthMode) {
        toggleAuthMode.classList.remove("hidden");
    }

    if (authSubmitBtn) {
        authSubmitBtn.textContent = isSignupMode
            ? "Create Account"
            : "Login";
    }

    if (forgotPasswordContainer) {
        forgotPasswordContainer.classList.toggle(
            "hidden",
            isSignupMode
        );
    }

    if (authMessage && !authMessage.textContent) {
        authMessage.textContent = "";
    }
}

// =====================================================
// AUTH FORM
// Student Login + Student Signup
// =====================================================

async function handleAuthSubmit(event) {
    event.preventDefault();

    const email =
        $("authEmail")?.value?.trim() || "";

    const password =
        $("authPassword")?.value || "";

    if (!email || !password) {
        showAuthMessage(
            "Please enter email and password.",
            "error"
        );
        return;
    }

    const submitButton =
        $("authSubmitBtn");

    if (submitButton) {
        submitButton.disabled = true;

        submitButton.textContent =
            isSignupMode
                ? "Creating Account..."
                : "Logging in...";
    }

    try {
        // =================================================
        // STUDENT SIGNUP
        // =================================================

        if (isSignupMode) {
            const name =
                $("regName")?.value?.trim() || "";

            const course =
                $("regCourse")?.value || "";

            const branch =
                $("regBranch")?.value || "";

            const year =
                $("regYear")?.value || "";

            const semester =
                $("regSemester")?.value || "";

            if (
                !name ||
                !course ||
                !branch ||
                !year ||
                !semester
            ) {
                showAuthMessage(
                    "Please fill all registration fields.",
                    "error"
                );

                return;
            }

            const {
                data,
                error
            } = await signUpStudent({
                email,
                password,
                name,
                course,
                branch,
                year,
                semester
            });

            if (error) {
                throw error;
            }

            // -------------------------------------------------
            // Email confirmation required
            // -------------------------------------------------

            if (
                data?.user &&
                !data?.session
            ) {
                showAuthMessage(
                    "Account created successfully. Please verify your email, then login.",
                    "success"
                );

                setSignupMode(false);
                resetAuthForm();
                updateAuthRoleUI();

                return;
            }

            // -------------------------------------------------
            // Session available immediately
            // -------------------------------------------------

            if (data?.session) {
                await initializeAuthenticatedUser();
                return;
            }

            // -------------------------------------------------
            // Signup successful but no session
            // -------------------------------------------------

            showAuthMessage(
                "Account created successfully. Please login.",
                "success"
            );

            setSignupMode(false);
            resetAuthForm();
            updateAuthRoleUI();

            return;
        }

        // =================================================
        // STUDENT LOGIN
        // =================================================

        const {
            data,
            error
        } = await signIn(
            email,
            password
        );

        if (error) {
            throw error;
        }

        if (!data?.user) {
            throw new Error(
                "Login failed. Please try again."
            );
        }

        // Load existing profile/role.
        await initializeAuthenticatedUser();

        // -------------------------------------------------
        // Prevent administrator account from using
        // normal Student Login.
        // -------------------------------------------------

        if (currentUserRole === "admin") {
            await logoutUser();

            throw new Error(
                "Administrator account detected. Please use the Administrator Login."
            );
        }

        showAuthMessage(
            "Login successful.",
            "success"
        );

    } catch (error) {
        console.error(
            "Authentication error:",
            error
        );

        showAuthMessage(
            getErrorMessage(error),
            "error"
        );

    } finally {
        if (submitButton) {
            submitButton.disabled = false;

            submitButton.textContent =
                isSignupMode
                    ? "Create Account"
                    : "Login";
        }
    }
}

// =====================================================
// LOGIN / SIGNUP TOGGLE
// =====================================================

function handleAuthModeToggle(event) {
    event.preventDefault();

    const newSignupMode =
        !isSignupMode;

    setSignupMode(
        newSignupMode
    );

    resetAuthForm();

    updateAuthRoleUI();

    if (newSignupMode) {
        updateRegistrationFields();
    }
}

// =====================================================
// FORGOT PASSWORD
// =====================================================

async function handleForgotPassword(event) {
    event.preventDefault();

    const email =
        $("authEmail")?.value?.trim() || "";

    if (!email) {
        showAuthMessage(
            "Please enter your email address first.",
            "error"
        );

        return;
    }

    const button =
        $("forgotPasswordBtn");

    if (button) {
        button.disabled = true;
        button.textContent = "Sending...";
    }

    try {
        const {
            error
        } = await resetPassword(email);

        if (error) {
            throw error;
        }

        showAuthMessage(
            "Password reset link has been sent to your email.",
            "success"
        );

    } catch (error) {
        console.error(
            "Password reset error:",
            error
        );

        showAuthMessage(
            getErrorMessage(error),
            "error"
        );

    } finally {
        if (button) {
            button.disabled = false;
            button.textContent =
                "Forgot Password?";
        }
    }
}

// =====================================================
// ADMIN LOGIN MODAL
// =====================================================

function openAdminLoginModal() {
    const modal =
        $("adminLoginModal");

    const step1 =
        $("adminStep1");

    const step2 =
        $("adminStep2");

    const idInput =
        $("adminLoginId");

    const passwordInput =
        $("adminLoginPassword");

    const message =
        $("adminLoginMessage");

    const stepText =
        $("adminLoginStepText");

    const idDisplay =
        $("adminLoginIdDisplay");

    adminLoginIdentifier = "";

    if (modal) {
        modal.classList.remove("hidden");
    }

    if (step1) {
        step1.classList.remove("hidden");
    }

    if (step2) {
        step2.classList.add("hidden");
    }

    if (idInput) {
        idInput.value = "";
    }

    if (passwordInput) {
        passwordInput.value = "";
        passwordInput.type = "password";
    }

    if (idDisplay) {
        idDisplay.textContent = "";
    }

    if (message) {
        message.textContent = "";
        message.className = "text-sm";
    }

    if (stepText) {
        stepText.textContent =
            "Step 1 of 2";
    }

    const toggleButton =
        $("toggleAdminPasswordBtn");

    if (toggleButton) {
        toggleButton.innerHTML =
            '<i class="fa-solid fa-eye"></i>';
    }

    setTimeout(() => {
        idInput?.focus();
    }, 100);
}

// =====================================================
// CLOSE ADMIN LOGIN MODAL
// =====================================================

function closeAdminLoginModal() {
    const modal =
        $("adminLoginModal");

    const idInput =
        $("adminLoginId");

    const passwordInput =
        $("adminLoginPassword");

    const message =
        $("adminLoginMessage");

    adminLoginIdentifier = "";

    if (modal) {
        modal.classList.add("hidden");
    }

    if (idInput) {
        idInput.value = "";
    }

    if (passwordInput) {
        passwordInput.value = "";
        passwordInput.type = "password";
    }

    if (message) {
        message.textContent = "";
    }

    const toggleButton =
        $("toggleAdminPasswordBtn");

    if (toggleButton) {
        toggleButton.innerHTML =
            '<i class="fa-solid fa-eye"></i>';
    }
}

// =====================================================
// ADMIN LOGIN STEP 1 → STEP 2
// =====================================================

function showAdminLoginStep2() {
    const idInput =
        $("adminLoginId");

    const step1 =
        $("adminStep1");

    const step2 =
        $("adminStep2");

    const idDisplay =
        $("adminLoginIdDisplay");

    const stepText =
        $("adminLoginStepText");

    const message =
        $("adminLoginMessage");

    const adminId =
        idInput?.value?.trim() || "";

    if (!adminId) {
        if (message) {
            message.textContent =
                "Please enter Administrator ID or email.";

            message.className =
                "text-sm text-red-600";
        }

        idInput?.focus();

        return;
    }

    adminLoginIdentifier =
        adminId;

    if (idDisplay) {
        idDisplay.textContent =
            adminId;
    }

    if (step1) {
        step1.classList.add("hidden");
    }

    if (step2) {
        step2.classList.remove("hidden");
    }

    if (stepText) {
        stepText.textContent =
            "Step 2 of 2";
    }

    if (message) {
        message.textContent = "";
    }

    setTimeout(() => {
        $("adminLoginPassword")?.focus();
    }, 100);
}

// =====================================================
// ADMIN LOGIN STEP 2 → STEP 1
// =====================================================

function showAdminLoginStep1() {
    const step1 =
        $("adminStep1");

    const step2 =
        $("adminStep2");

    const stepText =
        $("adminLoginStepText");

    const message =
        $("adminLoginMessage");

    const passwordInput =
        $("adminLoginPassword");

    if (step1) {
        step1.classList.remove("hidden");
    }

    if (step2) {
        step2.classList.add("hidden");
    }

    if (stepText) {
        stepText.textContent =
            "Step 1 of 2";
    }

    if (passwordInput) {
        passwordInput.value = "";
    }

    if (message) {
        message.textContent = "";
    }

    setTimeout(() => {
        $("adminLoginId")?.focus();
    }, 100);
}

// =====================================================
// ADMIN LOGIN SUBMIT
// =====================================================

async function handleAdminLogin() {
    const passwordInput =
        $("adminLoginPassword");

    const submitButton =
        $("adminLoginSubmitBtn");

    const message =
        $("adminLoginMessage");

    const password =
        passwordInput?.value || "";

    if (!adminLoginIdentifier) {
        showAdminLoginStep1();
        return;
    }

    if (!password) {
        if (message) {
            message.textContent =
                "Please enter your Administrator password.";

            message.className =
                "text-sm text-red-600";
        }

        passwordInput?.focus();

        return;
    }

    try {
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent =
                "Signing in...";
        }

        if (message) {
            message.textContent = "";
        }

        // -------------------------------------------------
        // Supabase login
        // -------------------------------------------------

        const {
            data,
            error
        } = await signIn(
            adminLoginIdentifier,
            password
        );

        if (error) {
            throw error;
        }

        if (!data?.user) {
            throw new Error(
                "Administrator login failed."
            );
        }

        // -------------------------------------------------
        // Load profile and role
        // -------------------------------------------------

        await initializeAuthenticatedUser();

        // -------------------------------------------------
        // Verify Admin role
        // -------------------------------------------------

        if (currentUserRole !== "admin") {
            console.warn(
                "Non-admin account attempted Administrator login."
            );

            await logoutUser();

            throw new Error(
                "This account is not an Administrator account."
            );
        }

        // -------------------------------------------------
        // SUCCESS
        // -------------------------------------------------

        closeAdminLoginModal();

        console.log(
            "Administrator login successful."
        );

    } catch (error) {
        console.error(
            "Administrator login error:",
            error
        );

        if (message) {
            message.textContent =
                getErrorMessage(error);

            message.className =
                "text-sm text-red-600";
        }

    } finally {
        if (submitButton) {
            submitButton.disabled = false;

            submitButton.textContent =
                "Administrator Login";
        }
    }
}

// =====================================================
// ADMIN PASSWORD TOGGLE
// =====================================================

function toggleAdminPassword() {
    const passwordInput =
        $("adminLoginPassword");

    const toggleButton =
        $("toggleAdminPasswordBtn");

    if (!passwordInput) {
        return;
    }

    const visible =
        passwordInput.type === "text";

    passwordInput.type =
        visible
            ? "password"
            : "text";

    if (toggleButton) {
        toggleButton.innerHTML =
            visible
                ? '<i class="fa-solid fa-eye"></i>'
                : '<i class="fa-solid fa-eye-slash"></i>';
    }
}

// =====================================================
// ADMIN LOGIN EVENTS
// =====================================================

function setupAdminLoginEvents() {
    const adminIcon =
        $("adminLoginIcon");

    const modal =
        $("adminLoginModal");

    const closeButton =
        $("closeAdminLoginBtn");

    const continueButton =
        $("adminContinueBtn");

    const submitButton =
        $("adminLoginSubmitBtn");

    const backButton =
        $("adminBackBtn");

    const passwordToggle =
        $("toggleAdminPasswordBtn");

    const idInput =
        $("adminLoginId");

    const passwordInput =
        $("adminLoginPassword");

    // -------------------------------------------------
    // Open
    // -------------------------------------------------

    adminIcon?.addEventListener(
        "click",
        openAdminLoginModal
    );

    // -------------------------------------------------
    // Close
    // -------------------------------------------------

    closeButton?.addEventListener(
        "click",
        closeAdminLoginModal
    );

    // -------------------------------------------------
    // Continue
    // -------------------------------------------------

    continueButton?.addEventListener(
        "click",
        showAdminLoginStep2
    );

    // -------------------------------------------------
    // Login
    // -------------------------------------------------

    submitButton?.addEventListener(
        "click",
        handleAdminLogin
    );

    // -------------------------------------------------
    // Back
    // -------------------------------------------------

    backButton?.addEventListener(
        "click",
        showAdminLoginStep1
    );

    // -------------------------------------------------
    // Password toggle
    // -------------------------------------------------

    passwordToggle?.addEventListener(
        "click",
        toggleAdminPassword
    );

    // -------------------------------------------------
    // Enter on Admin ID
    // -------------------------------------------------

    idInput?.addEventListener(
        "keydown",
        event => {
            if (event.key === "Enter") {
                event.preventDefault();
                showAdminLoginStep2();
            }
        }
    );

    // -------------------------------------------------
    // Enter on Password
    // -------------------------------------------------

    passwordInput?.addEventListener(
        "keydown",
        event => {
            if (event.key === "Enter") {
                event.preventDefault();
                handleAdminLogin();
            }
        }
    );

    // -------------------------------------------------
    // Click outside modal
    // -------------------------------------------------

    modal?.addEventListener(
        "click",
        event => {
            if (
                event.target ===
                event.currentTarget
            ) {
                closeAdminLoginModal();
            }
        }
    );
}

// =====================================================
// REGISTRATION COURSE CHANGE
// =====================================================

function handleRegistrationCourseChange() {
    updateRegistrationFields();
}

// =====================================================
// NOTES UPLOAD
// =====================================================

async function handleNotesUpload(event) {
    event.preventDefault();

    if (!isAdmin()) {
        alert(
            "Administrator access required."
        );
        return;
    }

    const form =
        event.currentTarget;

    const button =
        form.querySelector(
            'button[type="submit"]'
        );

    if (button) {
        button.disabled = true;
        button.textContent =
            "Uploading...";
    }

    try {
        const result =
            await uploadNotes(event);

        if (!result) {
            return;
        }

        updateNoteFields();

    } catch (error) {
        console.error(
            "Notes upload error:",
            error
        );

        alert(
            getErrorMessage(error)
        );

    } finally {
        if (button) {
            button.disabled = false;
            button.textContent =
                "Upload Notes";
        }
    }
}

// =====================================================
// TNP UPLOAD
// =====================================================

async function handleTnpUpload(event) {
    event.preventDefault();

    if (!isAdmin()) {
        alert(
            "Administrator access required."
        );
        return;
    }

    const form =
        event.currentTarget;

    const button =
        form.querySelector(
            'button[type="submit"]'
        );

    if (button) {
        button.disabled = true;
        button.textContent =
            "Uploading...";
    }

    try {
        const result =
            await uploadTnp(event);

        if (!result) {
            return;
        }

    } catch (error) {
        console.error(
            "TNP upload error:",
            error
        );

        alert(
            getErrorMessage(error)
        );

    } finally {
        if (button) {
            button.disabled = false;
            button.textContent =
                "Upload TNP";
        }
    }
}

// =====================================================
// PYQ UPLOAD
// =====================================================

async function handlePyqUpload(event) {
    event.preventDefault();

    if (!isAdmin()) {
        alert(
            "Administrator access required."
        );
        return;
    }

    const form =
        event.currentTarget;

    const button =
        form.querySelector(
            'button[type="submit"]'
        );

    if (button) {
        button.disabled = true;
        button.textContent =
            "Uploading...";
    }

    try {
        const result =
            await uploadPyq(event);

        if (!result) {
            return;
        }

        updatePyqFields();

    } catch (error) {
        console.error(
            "PYQ upload error:",
            error
        );

        alert(
            getErrorMessage(error)
        );

    } finally {
        if (button) {
            button.disabled = false;
            button.textContent =
                "Upload PYQ";
        }
    }
}

// =====================================================
// TIMETABLE UPLOAD
// =====================================================

async function handleTimetableUpload(event) {
    event.preventDefault();

    if (!isAdmin()) {
        alert(
            "Administrator access required."
        );
        return;
    }

    const form =
        event.currentTarget;

    const button =
        form.querySelector(
            'button[type="submit"]'
        );

    if (button) {
        button.disabled = true;
        button.textContent =
            "Uploading...";
    }

    try {
        const result =
            await uploadTimetable(event);

        if (!result) {
            return;
        }

        updateTimetableFields();

    } catch (error) {
        console.error(
            "Timetable upload error:",
            error
        );

        alert(
            getErrorMessage(error)
        );

    } finally {
        if (button) {
            button.disabled = false;
            button.textContent =
                "Upload Timetable";
        }
    }
}

// =====================================================
// RELOAD DATA
// =====================================================

async function reloadApplicationData() {
    await loadAllData(
        isAdmin()
    );

    updateSearchUI();

    updateStudentFilters();

    await refreshSearch();

    await renderCurrentCategory();
}

// =====================================================
// SEARCH EVENTS
// =====================================================

async function handleSearchChange() {
    updateSearchUI();

    updateStudentFilters();

    await refreshSearch();

    await renderCurrentCategory();
}

async function handleSearchInput() {
    await refreshSearch();

    await renderCurrentCategory();
}

// =====================================================
// CATEGORY BUTTON
// =====================================================

async function handleCategoryButtonClick(event) {
    const button =
        event.currentTarget;

    const category =
        button.dataset.dashboardCategory;

    if (!category) {
        return;
    }

    document
        .querySelectorAll(
            "[data-dashboard-category]"
        )
        .forEach(item => {
            item.classList.toggle(
                "active",
                item === button
            );
        });

    await handleCategoryChange(
        category
    );
}

// =====================================================
// ADMIN UPLOAD TYPE
// =====================================================

function handleAdminUploadTypeChange() {
    updateUploadPanel();
}

// =====================================================
// DATA REFRESH
// =====================================================

async function handleDataRefresh() {
    try {
        await reloadApplicationData();

    } catch (error) {
        console.error(
            "Data refresh error:",
            error
        );
    }
}

// =====================================================
// LOGOUT
// =====================================================

async function handleLogout() {
    try {
        await logoutUser();

        // Always return to Student Login.
        setSignupMode(false);

        updateAuthRoleUI();

    } catch (error) {
        console.error(
            "Logout error:",
            error
        );

        alert(
            getErrorMessage(error)
        );
    }
}

// =====================================================
// DELETE PROFILE
// =====================================================

function handleOpenDeleteProfile() {
    openDeleteProfileModal();
}

function handleCloseDeleteProfile() {
    closeDeleteProfileModal();
}

async function handleConfirmDeleteProfile() {
    try {
        await confirmDeleteProfile();

    } catch (error) {
        console.error(
            "Profile deletion error:",
            error
        );
    }
}

// =====================================================
// MOBILE SIDEBAR
// =====================================================

function setupMobileSidebar() {
    const openButton =
        $("mobileMenuBtn");

    const closeButton =
        $("closeSidebarBtn");

    const sidebar =
        $("sidebar");

    const overlay =
        $("sidebarOverlay");

    if (!sidebar) {
        return;
    }

    function openSidebar() {
        sidebar.classList.add(
            "mobile-open"
        );

        overlay?.classList.remove(
            "hidden"
        );
    }

    function closeSidebar() {
        sidebar.classList.remove(
            "mobile-open"
        );

        overlay?.classList.add(
            "hidden"
        );
    }

    openButton?.addEventListener(
        "click",
        openSidebar
    );

    closeButton?.addEventListener(
        "click",
        closeSidebar
    );

    overlay?.addEventListener(
        "click",
        closeSidebar
    );

    document
        .querySelectorAll(
            "[data-dashboard-category]"
        )
        .forEach(button => {
            button.addEventListener(
                "click",
                closeSidebar
            );
        });
}

// =====================================================
// PASSWORD TOGGLE
// =====================================================

function setupPasswordToggle() {
    const button =
        $("togglePasswordBtn");

    const password =
        $("authPassword");

    if (
        !button ||
        !password
    ) {
        return;
    }

    if (
        button.dataset.passwordReady ===
        "true"
    ) {
        return;
    }

    button.dataset.passwordReady =
        "true";

    button.addEventListener(
        "click",
        () => {
            const visible =
                password.type === "text";

            password.type =
                visible
                    ? "password"
                    : "text";

            const icon =
                button.querySelector("i");

            if (icon) {
                icon.classList.toggle(
                    "fa-eye",
                    visible
                );

                icon.classList.toggle(
                    "fa-eye-slash",
                    !visible
                );
            }
        }
    );
}

// =====================================================
// REGISTRATION EVENTS
// =====================================================

function setupRegistrationEvents() {
    const course =
        $("regCourse");

    course?.addEventListener(
        "change",
        handleRegistrationCourseChange
    );
}

// =====================================================
// ADMIN FIELD EVENTS
// =====================================================

function setupAdminFieldEvents() {
    const noteCourse =
        $("noteCourse");

    const pyqCourse =
        $("pyqCourse");

    const timetableCourse =
        $("timetableCourse");

    noteCourse?.addEventListener(
        "change",
        updateNoteFields
    );

    pyqCourse?.addEventListener(
        "change",
        updatePyqFields
    );

    timetableCourse?.addEventListener(
        "change",
        updateTimetableFields
    );
}

// =====================================================
// EDIT MODAL
// =====================================================

function setupEditModalEvents() {
    const form =
        $("editResourceForm");

    const modal =
        $("editResourceModal");

    const cancelButton =
        $("cancelEditResourceBtn");

    const closeButton =
        $("closeEditResourceBtn");

    if (!form) {
        return;
    }

    // -------------------------------------------------
    // Close modal
    // -------------------------------------------------

    function closeModal() {
        modal?.classList.add(
            "hidden"
        );

        form.reset();

        if (modal) {
            delete modal.dataset.category;
            delete modal.dataset.recordId;
        }
    }

    cancelButton?.addEventListener(
        "click",
        event => {
            event.preventDefault();
            closeModal();
        }
    );

    closeButton?.addEventListener(
        "click",
        event => {
            event.preventDefault();
            closeModal();
        }
    );

    modal?.addEventListener(
        "click",
        event => {
            if (
                event.target ===
                event.currentTarget
            ) {
                closeModal();
            }
        }
    );

    // -------------------------------------------------
    // Save edit
    // -------------------------------------------------

    form.addEventListener(
        "submit",
        async event => {
            event.preventDefault();

            if (!isAdmin()) {
                alert(
                    "Administrator access required."
                );

                return;
            }

            const category =
                modal?.dataset?.category ||
                $("editResourceCategory")?.value;

            const id =
                modal?.dataset?.recordId ||
                $("editResourceId")?.value;

            if (!category || !id) {
                alert(
                    "Unable to identify the resource."
                );

                return;
            }

            // -----------------------------------------
            // Common fields
            // -----------------------------------------

            const values = {};

            const subject =
                $("editResourceSubject");

            const title =
                $("editResourceTitle");

            const course =
                $("editResourceCourse");

            const branch =
                $("editResourceBranch");

            const year =
                $("editResourceYear");

            const semester =
                $("editResourceSemester");

            const examYear =
                $("editResourceExamYear");

            if (subject) {
                values.subject =
                    subject.value.trim();
            }

            if (title) {
                values.title =
                    title.value.trim();
            }

            if (course) {
                values.course =
                    course.value;
            }

            if (branch) {
                values.branch =
                    branch.value;
            }

            if (year) {
                values.year =
                    year.value === ""
                        ? null
                        : Number(year.value);
            }

            if (semester) {
                values.semester =
                    semester.value === ""
                        ? null
                        : Number(semester.value);
            }

            if (examYear) {
                values.exam_year =
                    examYear.value === ""
                        ? null
                        : Number(examYear.value);
            }

            // -----------------------------------------
            // Timetable fields
            // -----------------------------------------

            const examDate =
                $("editResourceDate");

            const startTime =
                $("editResourceStartTime");

            const endTime =
                $("editResourceEndTime");

            const examType =
                $("editResourceExamType");

            if (examDate) {
                values.exam_date =
                    examDate.value || null;
            }

            if (startTime) {
                values.start_time =
                    startTime.value || null;
            }

            if (endTime) {
                values.end_time =
                    endTime.value || null;
            }

            if (examType) {
                values.exam_type =
                    examType.value.trim();
            }

            const newFile =
                $("editResourceFile")
                    ?.files?.[0] || null;

            const saveButton =
                $("saveEditResourceBtn") ||
                form.querySelector(
                    'button[type="submit"]'
                );

            if (saveButton) {
                saveButton.disabled = true;
                saveButton.textContent =
                    "Saving...";
            }

            try {
                await editRecord(
                    category,
                    id,
                    values,
                    newFile
                );

                closeModal();

                await reloadApplicationData();

                alert(
                    "Resource updated successfully."
                );

            } catch (error) {
                console.error(
                    "Edit resource error:",
                    error
                );

                alert(
                    getErrorMessage(error)
                );

            } finally {
                if (saveButton) {
                    saveButton.disabled = false;
                    saveButton.textContent =
                        "Save Changes";
                }
            }
        }
    );
}

// =====================================================
// SEARCH EVENTS
// =====================================================

function setupSearchEvents() {
    const searchCategory =
        $("searchCategory");

    const searchInput =
        $("searchNotes");

    const ids = [
        "searchCourse",
        "searchBranch",
        "searchYear",
        "filterSemester",
        "searchSubject",

        "tnpSearchSubject",

        "pyqSearchCourse",
        "pyqSearchYear",
        "pyqSearchSemester",
        "pyqSearchSubject",

        "timetableSearchCourse",
        "timetableSearchSemester",
        "timetableSearchYear",
        "timetableSearchSubject"
    ];

    // -------------------------------------------------
    // Category dropdown
    // -------------------------------------------------

    searchCategory?.addEventListener(
        "change",
        async () => {
            const category =
                searchCategory.value ||
                "notes";

            await handleCategoryChange(
                category
            );
        }
    );

    // -------------------------------------------------
    // Search text
    // -------------------------------------------------

    searchInput?.addEventListener(
        "input",
        handleSearchInput
    );

    // -------------------------------------------------
    // Filters
    // -------------------------------------------------

    ids.forEach(id => {
        const element =
            $(id);

        if (!element) {
            return;
        }

        element.addEventListener(
            "change",
            handleSearchChange
        );
    });
}

// =====================================================
// CATEGORY BUTTONS
// =====================================================

function setupCategoryButtons() {
    document
        .querySelectorAll(
            "[data-dashboard-category]"
        )
        .forEach(button => {
            button.addEventListener(
                "click",
                handleCategoryButtonClick
            );
        });
}

// =====================================================
// PROFILE EVENTS
// =====================================================

function setupProfileEvents() {
    $("logoutBtn")?.addEventListener(
        "click",
        handleLogout
    );

    $("profileLogoutBtn")?.addEventListener(
        "click",
        handleLogout
    );

    $("deleteProfileBtn")?.addEventListener(
        "click",
        handleOpenDeleteProfile
    );

    $("cancelDeleteProfileBtn")?.addEventListener(
        "click",
        handleCloseDeleteProfile
    );

    $("confirmDeleteProfileBtn")?.addEventListener(
        "click",
        handleConfirmDeleteProfile
    );

    $("deleteProfileModal")?.addEventListener(
        "click",
        event => {
            if (
                event.target ===
                event.currentTarget
            ) {
                closeDeleteProfileModal();
            }
        }
    );
}

// =====================================================
// ADMIN EVENTS
// =====================================================

function setupAdminEvents() {
    $("adminUploadType")?.addEventListener(
        "change",
        handleAdminUploadTypeChange
    );

    $("uploadNotesForm")?.addEventListener(
        "submit",
        handleNotesUpload
    );

    $("uploadTnpForm")?.addEventListener(
        "submit",
        handleTnpUpload
    );

    $("uploadPyqForm")?.addEventListener(
        "submit",
        handlePyqUpload
    );

    $("uploadTimetableForm")?.addEventListener(
        "submit",
        handleTimetableUpload
    );

    // Telegram Community Settings
    setupTelegramSettingsEvents();
}

// =====================================================
// TELEGRAM COMMUNITY SETTINGS
// =====================================================

async function handleTelegramLinkUpdate() {
    if (!isAdmin()) {
        alert(
            "Administrator access required."
        );

        return;
    }

    const input =
        $("telegramLinkInput");

    const button =
        $("updateTelegramLinkBtn");

    const message =
        $("telegramSettingsMessage");

    const newLink =
        input?.value?.trim() || "";

    if (!newLink) {
        if (message) {
            message.textContent =
                "Please enter a Telegram link.";

            message.className =
                "text-sm mt-3 min-h-5 text-red-600";
        }

        input?.focus();

        return;
    }

    try {
        if (button) {
            button.disabled = true;

            button.innerHTML =
                '<i class="fa-solid fa-spinner fa-spin mr-2"></i> Updating...';
        }

        if (message) {
            message.textContent = "";
        }

        const savedLink =
            await updateTelegramLink(
                newLink
            );

        if (message) {
            message.textContent =
                "Telegram link updated successfully.";

            message.className =
                "text-sm mt-3 min-h-5 text-green-600";
        }

        console.log(
            "Telegram link updated:",
            savedLink
        );

    } catch (error) {
        console.error(
            "Telegram settings error:",
            error
        );

        if (message) {
            message.textContent =
                getErrorMessage(error);

            message.className =
                "text-sm mt-3 min-h-5 text-red-600";
        }

    } finally {
        if (button) {
            button.disabled = false;

            button.innerHTML =
                '<i class="fa-solid fa-rotate mr-2"></i> Update Link';
        }
    }
}

// =====================================================
// TELEGRAM SETTINGS EVENTS
// =====================================================

function setupTelegramSettingsEvents() {
    const button =
        $("updateTelegramLinkBtn");

    const input =
        $("telegramLinkInput");

    button?.addEventListener(
        "click",
        handleTelegramLinkUpdate
    );

    input?.addEventListener(
        "keydown",
        event => {
            if (event.key === "Enter") {
                event.preventDefault();

                handleTelegramLinkUpdate();
            }
        }
    );

}



// =====================================================
// CUSTOM EVENTS
// =====================================================

function setupCustomEvents() {
    window.addEventListener(
        "campusnotes:refresh",
        handleDataRefresh
    );

    /*
     * campusnotes:delete-resource
     * is handled inside admin.js.
     *
     * Do NOT register another delete listener here.
     */
}

// =====================================================
// SUPABASE AUTH STATE
// =====================================================

function setupAuthStateListener() {
    if (authListenerInitialized) {
        return;
    }

    authListenerInitialized = true;

    onAuthStateChange(
        async (
            event,
            session
        ) => {
            console.log(
                "Supabase auth event:",
                event
            );

            // ---------------------------------------------
            // LOGIN / SIGNUP
            // ---------------------------------------------

            if (
                event === "SIGNED_IN"
            ) {
                try {
                    await initializeAuthenticatedUser();

                } catch (error) {
                    console.error(
                        "Authenticated user initialization error:",
                        error
                    );
                }

                return;
            }

            // ---------------------------------------------
            // TOKEN REFRESH
            // ---------------------------------------------

            if (
                event === "TOKEN_REFRESHED"
            ) {
                return;
            }

            // ---------------------------------------------
            // USER UPDATED
            // ---------------------------------------------

            if (
                event === "USER_UPDATED"
            ) {
                if (session?.user) {
                    try {
                        await initializeAuthenticatedUser();

                    } catch (error) {
                        console.error(
                            "User update initialization error:",
                            error
                        );
                    }
                }

                return;
            }

            // ---------------------------------------------
            // SIGN OUT
            // ---------------------------------------------

            if (
                event === "SIGNED_OUT"
            ) {
                return;
            }
        }
    );
}

// =====================================================
// MAIN EVENT SETUP
// =====================================================

export function setupEvents() {
    if (eventsInitialized) {
        return;
    }

    eventsInitialized = true;

    console.log(
        "Initializing TechCampus_Hub events..."
    );

    // =================================================
    // INITIAL AUTH UI
    // =================================================

    // Main login is always Student login.
    setSignupMode(false);

    resetAuthForm();

    updateAuthRoleUI();

    // Keep existing UI auth mode setup if available.
    try {
        updateAuthModeUI();
    } catch (error) {
        console.warn(
            "updateAuthModeUI could not be executed:",
            error
        );
    }

    // =================================================
    // AUTH
    // =================================================

    $("authForm")?.addEventListener(
        "submit",
        handleAuthSubmit
    );

    $("toggleAuthMode")?.addEventListener(
        "click",
        handleAuthModeToggle
    );

    $("forgotPasswordBtn")?.addEventListener(
        "click",
        handleForgotPassword
    );

    // =================================================
    // ADMIN LOGIN MODAL
    // =================================================

    setupAdminLoginEvents();

    // =================================================
    // FIELDS
    // =================================================

    setupPasswordToggle();

    setupRegistrationEvents();

    setupAdminFieldEvents();

    // =================================================
    // SEARCH
    // =================================================

    setupSearchEvents();

    // =================================================
    // CATEGORY NAVIGATION
    // =================================================

    setupCategoryButtons();

    // =================================================
    // PROFILE
    // =================================================

    setupProfileEvents();

    // =================================================
    // ADMIN
    // =================================================

    setupAdminEvents();

    // =================================================
    // EDIT MODAL
    // =================================================

    setupEditModalEvents();

    // =================================================
    // MOBILE
    // =================================================

    setupMobileSidebar();

    // =================================================
    // CUSTOM EVENTS
    // =================================================

    setupCustomEvents();

    // =================================================
    // RENDER ACTIONS
    // =================================================

    setupRenderActions();

    // =================================================
    // ADMIN DYNAMIC ACTIONS
    // =================================================

    setupAdminDynamicActions();

    // =================================================
    // AUTH STATE
    // =================================================

    setupAuthStateListener();

    console.log(
        "TechCampus_Hub events initialized successfully."
    );
}


