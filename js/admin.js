
// =====================================================
// TechCampus_Hub
// admin.js
// Cloudinary-compatible Admin Management
// =====================================================

import { $ } from "./helpers.js";

import { supabase } from "../supabase.js";

import {
    STORAGE_BUCKET,
    ADMIN_EMAIL
} from "./config.js";

import {
    notes,
    tnpItems,
    pyqItems,
    timetableItems,
    reloadStudents
} from "./data.js";

import {
    uploadOptionalPdf,
    removeStorage,
    preparePdfReplacement
} from "./storage.js";

import {
    currentUserRole
} from "./state.js";


// =====================================================
// Admin Check
// =====================================================

function isAdmin() {
    return currentUserRole === "admin";
}


// =====================================================
// Ensure Admin
// =====================================================

function ensureAdmin() {
    if (!isAdmin()) {
        throw new Error(
            "Administrator access is required."
        );
    }
}


// =====================================================
// Value Helpers
// =====================================================

function getValue(id) {
    const element = $(id);

    return element
        ? String(element.value || "").trim()
        : "";
}


function getNumber(id) {
    const value = getValue(id);

    if (value === "") {
        return null;
    }

    const number = Number(value);

    return Number.isFinite(number)
        ? number
        : null;
}


function getFile(id) {
    const element = $(id);

    return element?.files?.[0] || null;
}


// =====================================================
// Admin Message
// =====================================================

export function showAdminMessage(
    message,
    type = "success"
) {
    const container = $("adminMessage");

    if (!container) {
        if (type === "error") {
            console.error(message);
        } else {
            console.log(message);
        }

        return;
    }

    container.textContent =
        String(message || "");

    container.className =
        "mb-4 rounded-xl px-4 py-3 text-sm";

    if (type === "error") {
        container.classList.add(
            "bg-red-50",
            "text-red-700",
            "border",
            "border-red-200"
        );
    } else {
        container.classList.add(
            "bg-green-50",
            "text-green-700",
            "border",
            "border-green-200"
        );
    }

    container.classList.remove("hidden");

    window.clearTimeout(
        showAdminMessage.timer
    );

    showAdminMessage.timer =
        window.setTimeout(() => {
            container.classList.add("hidden");
        }, 4000);
}


// =====================================================
// Refresh Application
// =====================================================

function dispatchRefresh() {
    window.dispatchEvent(
        new CustomEvent(
            "campusnotes:refresh"
        )
    );
}


// =====================================================
// Cloudinary Upload Result Helper
// =====================================================
//
// Cloudinary returns:
// {
//     file_url,
//     cloudinary_public_id,
//     file_name,
//     resource_type,
//     version
// }
//
// We save:
// file_url      -> actual Cloudinary URL
// storage_path  -> Cloudinary public_id
//
// This keeps compatibility with existing DB/code.
// =====================================================

function getUploadedFileUrl(uploaded) {
    return (
        uploaded?.file_url ||
        null
    );
}


function getUploadedPublicId(uploaded) {
    return (
        uploaded?.cloudinary_public_id ||
        uploaded?.storage_path ||
        null
    );
}


// =====================================================
// Notes Upload
// =====================================================

export async function uploadNotes(event) {
    event?.preventDefault();

    try {
        ensureAdmin();

        const subject =
            getValue("noteSubject");

        const title =
            getValue("noteTitle");

        const course =
            getValue("noteCourse");

        const branch =
            getValue("noteBranch");

        const year =
            getNumber("noteYear");

        const semester =
            getNumber("noteSemester");

        const file =
            getFile("noteFile");


        // -------------------------------------------------
        // Validation
        // -------------------------------------------------

        if (!subject) {
            throw new Error(
                "Please enter the subject."
            );
        }

        if (!course) {
            throw new Error(
                "Please select the course."
            );
        }

        if (!branch) {
            throw new Error(
                "Please select the branch."
            );
        }

        if (!year) {
            throw new Error(
                "Please select the year."
            );
        }

        if (!semester) {
            throw new Error(
                "Please select the semester."
            );
        }

        if (!file) {
            throw new Error(
                "Please select a PDF file."
            );
        }


        // -------------------------------------------------
        // Upload PDF to Cloudinary
        // -------------------------------------------------

        const uploaded =
            await uploadOptionalPdf(
                file,
                "notes"
            );


        const fileUrl =
            getUploadedFileUrl(uploaded);

        const publicId =
            getUploadedPublicId(uploaded);


        if (!fileUrl) {
            throw new Error(
                "Cloudinary upload succeeded, but no file URL was returned."
            );
        }


        // -------------------------------------------------
        // Save Database Record
        // -------------------------------------------------

        try {

            const {
                data,
                error
            } = await supabase
                .from("notes")
                .insert({
                    subject,
                    title:
                        title || null,

                    course,
                    branch,
                    year,
                    semester,

                    // Cloudinary URL
                    file_url:
                        fileUrl,

                    // Cloudinary public ID
                    // kept in existing storage_path column
                    storage_path:
                        publicId
                })
                .select()
                .single();


            if (error) {
                throw error;
            }


            showAdminMessage(
                "Note uploaded successfully."
            );


            event?.target?.reset();

            dispatchRefresh();

            return data;

        } catch (error) {

            // Cloudinary frontend cannot securely
            // delete assets without API secret.
            // removeStorage() handles this safely.
            if (publicId) {
                try {
                    await removeStorage(
                        publicId
                    );
                } catch (cleanupError) {
                    console.error(
                        "Notes upload cleanup error:",
                        cleanupError
                    );
                }
            }

            throw error;
        }

    } catch (error) {

        console.error(
            "Upload notes error:",
            error
        );

        showAdminMessage(
            error.message ||
                "Unable to upload note.",
            "error"
        );

        return null;
    }
}


// =====================================================
// TNP Upload
// =====================================================

export async function uploadTnp(event) {
    event?.preventDefault();

    try {
        ensureAdmin();

        const subject =
            getValue("tnpSubject");

        const title =
            getValue("tnpTitle");

        const file =
            getFile("tnpFile");


        // -------------------------------------------------
        // Validation
        // -------------------------------------------------

        if (!subject) {
            throw new Error(
                "Please enter the subject."
            );
        }

        if (!file) {
            throw new Error(
                "Please select a PDF file."
            );
        }


        // -------------------------------------------------
        // Upload to Cloudinary
        // -------------------------------------------------

        const uploaded =
            await uploadOptionalPdf(
                file,
                "tnp"
            );


        const fileUrl =
            getUploadedFileUrl(uploaded);

        const publicId =
            getUploadedPublicId(uploaded);


        if (!fileUrl) {
            throw new Error(
                "Cloudinary upload succeeded, but no file URL was returned."
            );
        }


        // -------------------------------------------------
        // Save TNP Record
        // -------------------------------------------------

        try {

            const {
                data,
                error
            } = await supabase
                .from("tnp")
                .insert({
                    subject,

                    title:
                        title || null,

                    // IMPORTANT:
                    // Save Cloudinary URL
                    file_url:
                        fileUrl,

                    // Save Cloudinary public ID
                    storage_path:
                        publicId
                })
                .select()
                .single();


            if (error) {
                throw error;
            }


            showAdminMessage(
                "TNP resource uploaded successfully."
            );


            event?.target?.reset();

            dispatchRefresh();

            return data;

        } catch (error) {

            if (publicId) {
                try {
                    await removeStorage(
                        publicId
                    );
                } catch (cleanupError) {
                    console.error(
                        "TNP upload cleanup error:",
                        cleanupError
                    );
                }
            }

            throw error;
        }

    } catch (error) {

        console.error(
            "Upload TNP error:",
            error
        );

        showAdminMessage(
            error.message ||
                "Unable to upload TNP resource.",
            "error"
        );

        return null;
    }
}


// =====================================================
// PYQ Upload
// =====================================================

export async function uploadPyq(event) {
    event?.preventDefault();

    try {
        ensureAdmin();

        const course =
            getValue("pyqCourse");

        const year =
            getNumber("pyqYear");

        const semester =
            getNumber("pyqSemester");

        const subject =
            getValue("pyqSubject");

        const title =
            getValue("pyqTitle");

        const file =
            getFile("pyqFile");


        // -------------------------------------------------
        // Validation
        // -------------------------------------------------

        if (!course) {
            throw new Error(
                "Please select the course."
            );
        }

        if (!year) {
            throw new Error(
                "Please select the previous year."
            );
        }

        if (!semester) {
            throw new Error(
                "Please select the semester."
            );
        }

        if (!subject) {
            throw new Error(
                "Please enter the subject."
            );
        }

        if (!file) {
            throw new Error(
                "Please select a PDF file."
            );
        }


        // -------------------------------------------------
        // Upload to Cloudinary
        // -------------------------------------------------

        const uploaded =
            await uploadOptionalPdf(
                file,
                "pyq"
            );


        const fileUrl =
            getUploadedFileUrl(uploaded);

        const publicId =
            getUploadedPublicId(uploaded);


        if (!fileUrl) {
            throw new Error(
                "Cloudinary upload succeeded, but no file URL was returned."
            );
        }


        // -------------------------------------------------
        // Save PYQ Record
        // -------------------------------------------------

        try {

            const {
                data,
                error
            } = await supabase
                .from("previousYearPapers")
                .insert({
                    course,
                    year,
                    semester,
                    subject,

                    // PYQ title
                    title:
                        title || null,

                    // Cloudinary URL
                    file_url:
                        fileUrl,

                    // Cloudinary public ID
                    storage_path:
                        publicId
                })
                .select()
                .single();


            if (error) {
                throw error;
            }


            showAdminMessage(
                "Previous year paper uploaded successfully."
            );


            event?.target?.reset();

            dispatchRefresh();

            return data;

        } catch (error) {

            if (publicId) {
                try {
                    await removeStorage(
                        publicId
                    );
                } catch (cleanupError) {
                    console.error(
                        "PYQ upload cleanup error:",
                        cleanupError
                    );
                }
            }

            throw error;
        }

    } catch (error) {

        console.error(
            "Upload PYQ error:",
            error
        );

        showAdminMessage(
            error.message ||
                "Unable to upload previous year paper.",
            "error"
        );

        return null;
    }
}

// =====================================================
// Timetable Upload
// =====================================================

export async function uploadTimetable(event) {
    event?.preventDefault();

    let publicId = null;

    try {
        ensureAdmin();

        const course =
            getValue("timetableCourse");

        const semester =
            getNumber("timetableSemester");

        const examYear =
            getNumber("timetableExamYear");

        const examType =
            getValue("timetableExamType");

        const file =
            getFile("timetableFile");


        // -------------------------------------------------
        // Validation
        // -------------------------------------------------

        if (!course) {
            throw new Error(
                "Please select the course."
            );
        }

        if (!semester) {
            throw new Error(
                "Please select the semester."
            );
        }

        if (!examYear) {
            throw new Error(
                "Please enter the exam year."
            );
        }

        if (!examType) {
            throw new Error(
                "Please select the exam type."
            );
        }

        if (!file) {
            throw new Error(
                "Please select a timetable file."
            );
        }


        // -------------------------------------------------
        // Upload Timetable File to Cloudinary
        // -------------------------------------------------

        const uploaded =
            await uploadOptionalPdf(
                file,
                "timetable"
            );


        const fileUrl =
            getUploadedFileUrl(uploaded);

        publicId =
            getUploadedPublicId(uploaded);


        if (!fileUrl) {
            throw new Error(
                "Cloudinary upload succeeded, but no file URL was returned."
            );
        }


        // -------------------------------------------------
        // Save Timetable Record in Supabase
        // -------------------------------------------------

        try {

            const {
                data,
                error
            } = await supabase
                .from("timetable")
                .insert({
                    course:
                        course,

                    semester:
                        semester,

                    exam_year:
                        examYear,

                    exam_type:
                        examType,

                    file_url:
                        fileUrl,

                    cloudinary_public_id:
                        publicId,

                    file_name:
                        file.name
                })
                .select()
                .single();


            if (error) {
                throw error;
            }


            // -------------------------------------------------
            // Success
            // -------------------------------------------------

            showAdminMessage(
                "Exam timetable uploaded successfully."
            );


            event?.target?.reset();

            dispatchRefresh();

            return data;

        } catch (databaseError) {

            // Database insert failed.
            // Try safe cleanup through storage.js.
            if (publicId) {
                try {
                    await removeStorage(
                        publicId
                    );
                } catch (cleanupError) {
                    console.error(
                        "Timetable upload cleanup error:",
                        cleanupError
                    );
                }
            }

            throw databaseError;
        }


    } catch (error) {

        console.error(
            "Upload timetable error:",
            error
        );

        showAdminMessage(
            error.message ||
                "Unable to upload timetable.",
            "error"
        );

        return null;
    }
}

// =====================================================
// Find Record
// =====================================================

export function findRecord(
    category,
    id
) {
    const targetId =
        String(id);

    let source = [];

    switch (category) {

        case "tnp":
            source = tnpItems;
            break;

        case "pyq":
            source = pyqItems;
            break;

        case "timetable":
            source = timetableItems;
            break;

        case "notes":
        default:
            source = notes;
            break;
    }

    return (
        source.find(
            item =>
                String(item.id) === targetId
        ) || null
    );
}


// =====================================================
// Edit Notes
// =====================================================

async function editNotes(
    id,
    values = {}
) {
    ensureAdmin();

    const record =
        findRecord(
            "notes",
            id
        );

    if (!record) {
        throw new Error(
            "Note not found."
        );
    }

    const payload = {
        subject:
            values.subject ??
            record.subject,

        title:
            values.title ??
            record.title,

        course:
            values.course ??
            record.course,

        branch:
            values.branch ??
            record.branch,

        year:
            values.year ??
            record.year,

        semester:
            values.semester ??
            record.semester
    };

    const {
        data,
        error
    } = await supabase
        .from("notes")
        .update(payload)
        .eq("id", id)
        .select()
        .single();

    if (error) {
        throw error;
    }

    return data;
}


// =====================================================
// Edit TNP
// =====================================================

async function editTnp(
    id,
    values = {}
) {
    ensureAdmin();

    const record =
        findRecord(
            "tnp",
            id
        );

    if (!record) {
        throw new Error(
            "TNP resource not found."
        );
    }

    const payload = {
        subject:
            values.subject ??
            record.subject,

        title:
            values.title ??
            record.title
    };

    const {
        data,
        error
    } = await supabase
        .from("tnp")
        .update(payload)
        .eq("id", id)
        .select()
        .single();

    if (error) {
        throw error;
    }

    return data;
}


// =====================================================
// Edit PYQ
// =====================================================

async function editPyq(
    id,
    values = {}
) {
    ensureAdmin();

    const record =
        findRecord(
            "pyq",
            id
        );

    if (!record) {
        throw new Error(
            "Previous year paper not found."
        );
    }

    const payload = {
        course:
            values.course ??
            record.course,

        year:
            values.year ??
            record.year,

        semester:
            values.semester ??
            record.semester,

        subject:
            values.subject ??
            record.subject,

        title:
            values.title ??
            record.title
    };

    const {
        data,
        error
    } = await supabase
        .from("previousYearPapers")
        .update(payload)
        .eq("id", id)
        .select()
        .single();

    if (error) {
        throw error;
    }

    return data;
}

// =====================================================
// Edit Timetable
// =====================================================

async function editTimetable(
    id,
    values = {}
) {
    ensureAdmin();

    const record =
        findRecord(
            "timetable",
            id
        );

    if (!record) {
        throw new Error(
            "Timetable record not found."
        );
    }


    const payload = {

        course:
            values.course ??
            record.course,

        semester:
            values.semester ??
            record.semester,

        exam_year:
            values.exam_year ??
            record.exam_year,

        exam_type:
            values.exam_type ??
            record.exam_type
    };


    const {
        data,
        error
    } = await supabase
        .from("timetable")
        .update(payload)
        .eq("id", id)
        .select()
        .single();


    if (error) {
        throw error;
    }


    return data;
}

// =====================================================
// Get Table Name
// =====================================================

function getTableName(category) {
    switch (category) {

        case "tnp":
            return "tnp";

        case "pyq":
            return "previousYearPapers";

        case "timetable":
            return "timetable";

        case "notes":
        default:
            return "notes";
    }
}


// =====================================================
// Replace PDF
// =====================================================
//
// Cloudinary version:
//
// 1. Upload new PDF
// 2. Get new Cloudinary URL
// 3. Get new Cloudinary public ID
// 4. Update Supabase record
//
// IMPORTANT:
// Cloudinary deletion requires API secret.
// Frontend must NOT contain Cloudinary API secret.
// Therefore removeStorage() safely handles cleanup.
// =====================================================

async function replaceRecordPdf(
    category,
    id,
    newFile
) {
    ensureAdmin();

    if (!newFile) {
        return null;
    }


    const record =
        findRecord(
            category,
            id
        );

    if (!record) {
        throw new Error(
            "Resource not found."
        );
    }


    // -------------------------------------------------
    // Upload replacement to Cloudinary
    // -------------------------------------------------

    const replacement =
        await preparePdfReplacement(
            newFile,
            category
        );


    const newFileUrl =
        replacement?.file_url ||
        null;


    const newPublicId =
        replacement?.cloudinary_public_id ||
        replacement?.storage_path ||
        null;


    if (!newFileUrl) {
        try {
            await replacement?.cleanup?.();
        } catch (cleanupError) {
            console.error(
                "Replacement cleanup error:",
                cleanupError
            );
        }

        throw new Error(
            "New PDF uploaded but Cloudinary URL was not returned."
        );
    }


    if (!newPublicId) {
        try {
            await replacement?.cleanup?.();
        } catch (cleanupError) {
            console.error(
                "Replacement cleanup error:",
                cleanupError
            );
        }

        throw new Error(
            "New PDF uploaded but Cloudinary public ID was not returned."
        );
    }


    const oldPublicId =
        record.storage_path ||
        null;


    try {

        const table =
            getTableName(category);


        // -------------------------------------------------
        // Update Supabase
        // -------------------------------------------------

        const {
            data,
            error
        } = await supabase
            .from(table)
            .update({
                // New Cloudinary public ID
                storage_path:
                    newPublicId,

                // New Cloudinary URL
                file_url:
                    newFileUrl
            })
            .eq("id", id)
            .select()
            .single();


        if (error) {
            throw error;
        }


        // Tell storage.js that upload is now committed
        if (
            typeof replacement.markCommitted ===
            "function"
        ) {
            replacement.markCommitted();
        }


        // -------------------------------------------------
        // Try to clean old Cloudinary asset
        // -------------------------------------------------

        if (
    oldPublicId &&
    oldPublicId !== newPublicId
) {

    try {

        await removeStorage(
            oldPublicId,
            replacement.resource_type ||
            record.resource_type ||
            null
        );

    } catch (storageError) {

        console.error(
            "Old Cloudinary PDF deletion error:",
            storageError
        );

        showAdminMessage(
            "Resource updated, but old PDF could not be deleted.",
            "error"
        );
    }
}

        return data;

    } catch (error) {

        // If DB update failed, clean the new upload
        // when possible.
        try {

            if (
                typeof replacement.cleanup ===
                "function"
            ) {
                await replacement.cleanup();
            }

        } catch (cleanupError) {

            console.error(
                "Replacement cleanup error:",
                cleanupError
            );
        }


        throw error;
    }
}


// =====================================================
// Edit Complete Record
// =====================================================

export async function editRecord(
    category,
    id,
    values = {},
    newFile = null
) {
    ensureAdmin();

    let updatedRecord;


    switch (category) {

        case "tnp":

            updatedRecord =
                await editTnp(
                    id,
                    values
                );

            break;


        case "pyq":

            updatedRecord =
                await editPyq(
                    id,
                    values
                );

            break;


        case "timetable":

            updatedRecord =
                await editTimetable(
                    id,
                    values
                );

            break;


        case "notes":
        default:

            updatedRecord =
                await editNotes(
                    id,
                    values
                );

            break;
    }


    // -------------------------------------------------
    // Replace PDF if new file selected
    // -------------------------------------------------

    if (newFile) {

        updatedRecord =
            await replaceRecordPdf(
                category,
                id,
                newFile
            );
    }


    showAdminMessage(
        "Resource updated successfully."
    );


    dispatchRefresh();

    return updatedRecord;
}


// =====================================================
// Delete Record
// =====================================================

export async function deleteRecord(
    category,
    id
) {
    ensureAdmin();


    const record =
        findRecord(
            category,
            id
        );

    if (!record) {
        throw new Error(
            "Resource not found."
        );
    }


    const table =
        getTableName(category);


    // -------------------------------------------------
    // Delete database record
    // -------------------------------------------------

    const {
        error
    } = await supabase
        .from(table)
        .delete()
        .eq("id", id);


    if (error) {
        throw error;
    }


    // -------------------------------------------------
    // Cloudinary cleanup
    // -------------------------------------------------
    //
    // Frontend cannot securely delete Cloudinary
    // assets because API Secret must remain private.
    //
    // removeStorage() safely handles this.
    // -------------------------------------------------

    if (record.storage_path) {

        try {

            await removeStorage(
                record.storage_path
            );

        } catch (storageError) {

            console.error(
                "Storage cleanup error:",
                storageError
            );

            showAdminMessage(
                "Record deleted, but the Cloudinary PDF could not be removed.",
                "error"
            );
        }
    }


    showAdminMessage(
        "Resource deleted successfully."
    );


    dispatchRefresh();

    return true;
}


// =====================================================
// Admin Upload UI
// =====================================================
//
// Compatibility function.
// Main application UI uses updateUploadPanel()
// from ui.js.
// =====================================================

export function updateAdminUploadUI() {

    const typeSelect =
        $("adminUploadType");


    if (!typeSelect) {
        return;
    }


    const selectedType =
        typeSelect.value;


    const forms = {

        notes:
            $("uploadNotesForm"),

        tnp:
            $("uploadTnpForm"),

        pyq:
            $("uploadPyqForm"),

        timetable:
            $("uploadTimetableForm")
    };


    Object.entries(forms).forEach(
        ([type, form]) => {

            if (!form) {
                return;
            }

            form.classList.toggle(
                "hidden",
                type !== selectedType
            );
        }
    );
}


// =====================================================
// Student Management
// =====================================================

export async function getStudents() {

    ensureAdmin();

    return await reloadStudents();
}


// =====================================================
// Delete Student Profile
// =====================================================
//
// Actual Auth deletion happens inside the secure
// delete-account Edge Function.
//
// Frontend never receives or handles passwords.
// =====================================================

export async function deleteStudentProfile(
    studentId
) {
    ensureAdmin();


    if (!studentId) {
        throw new Error(
            "Student ID is required."
        );
    }


    const {
        data: {
            user
        },
        error: userError
    } = await supabase.auth.getUser();


    if (userError) {
        throw userError;
    }


    if (!user?.id) {
        throw new Error(
            "No authenticated administrator found."
        );
    }


    if (
        String(user.id) ===
        String(studentId)
    ) {
        throw new Error(
            "Administrator account cannot be deleted from Student Management."
        );
    }


    const {
        data,
        error
    } = await supabase.functions.invoke(
        "delete-account",
        {
            body: {
                targetUserId:
                    String(studentId)
            }
        }
    );


    if (error) {

        console.error(
            "Student deletion error:",
            error
        );

        throw error;
    }


    await reloadStudents();

    dispatchRefresh();

    return data;
}


// =====================================================
// Edit Modal Helpers
// =====================================================

function setFieldValue(
    id,
    value
) {
    const element =
        $(id);


    if (!element) {
        return;
    }


    element.value =
        value === null ||
        value === undefined
            ? ""
            : String(value);
}


function showEditField(
    id,
    visible
) {
    const element =
        $(id);


    if (!element) {
        return;
    }


    const group =
        element.closest(
            ".edit-field-group"
        ) ||
        element.closest(
            ".filter-group"
        ) ||
        element.parentElement;


    if (group) {

        group.classList.toggle(
            "hidden",
            !visible
        );
    }
}


function clearEditFileInput() {

    const input =
        $("editResourceFile");


    if (input) {
        input.value = "";
    }
}

// =====================================================
// TIMETABLE SEMESTER OPTIONS
// MCA = 4 Semesters
// B.Tech = 8 Semesters
// =====================================================

function updateTimetableSemesterOptions(
    course,
    semesterSelectId
) {
    const semesterSelect =
        $(semesterSelectId);

    if (!semesterSelect) {
        return;
    }

    const selectedSemester =
        semesterSelect.value;

    semesterSelect.innerHTML = `
        <option value="">
            Select Semester
        </option>
    `;

    let totalSemesters = 0;

    if (course === "MCA") {
        totalSemesters = 4;
    }

    else if (
        course === "B.Tech" ||
        course === "BTech"
    ) {
        totalSemesters = 8;
    }

    for (
        let semester = 1;
        semester <= totalSemesters;
        semester++
    ) {
        const option =
            document.createElement("option");

        option.value =
            String(semester);

        option.textContent =
            `Semester ${semester}`;

        semesterSelect.appendChild(
            option
        );
    }

    // Keep previous value if valid
    if (
        selectedSemester &&
        Number(selectedSemester) <=
            totalSemesters
    ) {
        semesterSelect.value =
            selectedSemester;
    }
}

// =====================================================
// Populate Edit Modal
// ====================================================
function populateEditModal(
    category,
    record
) {
    setFieldValue(
        "editResourceId",
        record.id
    );

    setFieldValue(
        "editResourceCategory",
        category
    );

    const isNotes =
        category === "notes";

    const isTnp =
        category === "tnp";

    const isPyq =
        category === "pyq";

    const isTimetable =
        category === "timetable";


    // =====================================================
    // Make sure SELECT has the current value as an option
    // =====================================================

    function ensureSelectOption(
        fieldId,
        value,
        label = value
    ) {
        const select = $(fieldId);

        if (!select || value === undefined || value === null || value === "") {
            return;
        }

        const stringValue = String(value);

        const exists = Array.from(
            select.options
        ).some(
            option =>
                String(option.value) === stringValue
        );

        if (!exists) {
            const option =
                document.createElement("option");

            option.value = stringValue;
            option.textContent = label;

            select.appendChild(option);
        }

        select.value = stringValue;
    }


    // =====================================================
    // Normal Fields
    // =====================================================

    setFieldValue(
        "editResourceSubject",
        record.subject || ""
    );

    setFieldValue(
        "editResourceTitle",
        record.title || ""
    );

    setFieldValue(
        "editResourceCourse",
        record.course || ""
    );

    setFieldValue(
        "editResourceBranch",
        record.branch || ""
    );

    setFieldValue(
        "editResourceYear",
        record.year || ""
    );

    setFieldValue(
        "editResourceSemester",
        record.semester || ""
    );

    setFieldValue(
        "editResourceExamYear",
        record.exam_year || ""
    );


    // =====================================================
    // TIMETABLE
    // =====================================================
if (isTimetable) {

    // -------------------------------------------------
    // Course
    // -------------------------------------------------

    ensureSelectOption(
        "editResourceCourse",
        record.course
    );


    // -------------------------------------------------
    // Semester according to Course
    // -------------------------------------------------

    updateTimetableSemesterOptions(
        record.course,
        "editResourceSemester"
    );


    const editSemester =
        $("editResourceSemester");

    if (editSemester) {

        editSemester.value =
            String(record.semester || "");
    }


    // -------------------------------------------------
    // Exam Year
    // Admin manually enters the year
    // -------------------------------------------------

    setFieldValue(
        "editResourceExamYear",
        record.exam_year || ""
    );


    // -------------------------------------------------
    // Exam Type
    // -------------------------------------------------

    const examTypeSelect =
        $("editResourceExamType");

    if (examTypeSelect) {

        examTypeSelect.value =
            record.exam_type || "";
    }
}

    // =====================================================
    // Clear New File
    // =====================================================

    clearEditFileInput();

    // =====================================================
    // Show / Hide Fields
    // =====================================================

    // Subject
    // Timetable me subject nahi chahiye.

    showEditField(
        "editResourceSubject",
        isNotes ||
        isTnp ||
        isPyq
    );


    // Title
    showEditField(
        "editResourceTitle",
        isNotes ||
        isTnp ||
        isPyq
    );


    // Course
    showEditField(
        "editResourceCourse",
        isNotes ||
        isPyq ||
        isTimetable
    );


    // Branch
    showEditField(
        "editResourceBranch",
        isNotes
    );


    // Year
    showEditField(
        "editResourceYear",
        isNotes
    );


    // Semester
    showEditField(
        "editResourceSemester",
        isNotes ||
        isPyq ||
        isTimetable
    );


    // Exam Year
    showEditField(
        "editResourceExamYear",
        isTimetable
    );


    // Exam Type
    showEditField(
        "editResourceExamType",
        isTimetable
    );
}

// =====================================================
// Open Edit Dialog
// =====================================================

export function openEditDialog(
    category,
    id
) {
    ensureAdmin();


    const record =
        findRecord(
            category,
            id
        );


    if (!record) {

        showAdminMessage(
            "Resource not found.",
            "error"
        );

        return null;
    }


    window.dispatchEvent(
        new CustomEvent(
            "campusnotes:open-edit",
            {
                detail: {
                    category,
                    record
                }
            }
        )
    );


    return record;
}


// =====================================================
// Setup Admin Dynamic Actions
// =====================================================

export function setupAdminDynamicActions() {

    if (
        document.body.dataset
            .adminActionsReady === "true"
    ) {
        return;
    }


    document.body.dataset
        .adminActionsReady = "true";


    // -------------------------------------------------
    // Edit Button
    // -------------------------------------------------

    window.addEventListener(
        "campusnotes:edit-resource",
        event => {

            if (!isAdmin()) {
                return;
            }


            const {
                category,
                id
            } = event.detail || {};


            if (!category || !id) {
                return;
            }


            openEditDialog(
                category,
                id
            );
        }
    );


    // -------------------------------------------------
    // Delete Button
    // -------------------------------------------------

    window.addEventListener(
        "campusnotes:delete-resource",
        async event => {

            if (!isAdmin()) {
                return;
            }


            const {
                category,
                id
            } = event.detail || {};


            if (!category || !id) {
                return;
            }


            const record =
                findRecord(
                    category,
                    id
                );


            if (!record) {

                showAdminMessage(
                    "Resource not found.",
                    "error"
                );

                return;
            }


            const title =
                record.title ||
                record.subject ||
                "this resource";


            const confirmed =
                window.confirm(
                    `Are you sure you want to delete "${title}"?`
                );


            if (!confirmed) {
                return;
            }


            try {

                await deleteRecord(
                    category,
                    id
                );

            } catch (error) {

                console.error(
                    "Delete resource error:",
                    error
                );


                showAdminMessage(
                    error.message ||
                        "Unable to delete resource.",
                    "error"
                );
            }
        }
    );


    // -------------------------------------------------
    // Open Edit Modal
    // -------------------------------------------------

    window.addEventListener(
        "campusnotes:open-edit",
        event => {

            if (!isAdmin()) {
                return;
            }


            const {
                category,
                record
            } = event.detail || {};


            if (!category || !record) {
                return;
            }


            const editModal =
                $("editResourceModal");


            if (!editModal) {

                showAdminMessage(
                    "Edit form is not available.",
                    "error"
                );

                return;
            }


            editModal.dataset.category =
                category;


            editModal.dataset.recordId =
                String(record.id);


            populateEditModal(
                category,
                record
            );


            editModal.classList.remove(
                "hidden"
            );
        }
    );
}

// =====================================================
// Setup Admin
// =====================================================

export function setupAdmin() {

    if (!isAdmin()) {
        return;
    }

    const adminUploadType =
        $("adminUploadType");

    if (adminUploadType) {

        adminUploadType.addEventListener(
            "change",
            updateAdminUploadUI
        );

        updateAdminUploadUI();
    }

    const timetableCourse =
    $("timetableCourse");

if (timetableCourse) {

    timetableCourse.addEventListener(
        "change",
        () => {

            updateTimetableSemesterOptions(
                timetableCourse.value,
                "timetableSemester"
            );

        }
    );

}

    setupAdminDynamicActions();

}

// =====================================================
// Prevent unused import warnings
// =====================================================

void ADMIN_EMAIL;
void STORAGE_BUCKET;

