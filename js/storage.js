
// =====================================================
// TechCampus_Hub
// storage.js
// Cloudinary PDF Storage
// =====================================================

import { supabase } from "../supabase.js";


const CLOUDINARY_CLOUD_NAME = "f6yh5vch";
const CLOUDINARY_UPLOAD_PRESET = "campus_notes";

// Cloudinary automatically detects the uploaded asset type.
// This works for PDF files.

const CLOUDINARY_UPLOAD_URL =
    `https://api.cloudinary.com/v1_1/${CLOUDINARY_CLOUD_NAME}/auto/upload`;


// =====================================================
// Missing PDF URLs
// =====================================================

export const missingPdfPaths = new Set();


// =====================================================
// Get PDF URL
// =====================================================
//
// Cloudinary file URL is already public/deliverable.
// No Supabase signed URL is required.
// =====================================================

export async function getPdfUrl(item) {

    if (!item) {
        return "";
    }

    // If a direct URL was passed
    if (typeof item === "string") {
        return item;
    }

    // New Cloudinary database field
    if (item.file_url) {
        return item.file_url;
    }

    // Backward compatibility:
    // Some old records may still contain a URL in storage_path.
    if (
        item.storage_path &&
        String(item.storage_path).startsWith("http")
    ) {
        return item.storage_path;
    }

    return "";
}


// =====================================================
// Check PDF File
// =====================================================

export function isPdfFile(file) {

    if (!file) {
        return false;
    }

    const fileName =
        String(file.name || "").toLowerCase();

    const fileType =
        String(file.type || "").toLowerCase();

    return (
        fileType === "application/pdf" ||
        fileName.endsWith(".pdf")
    );
}


// =====================================================
// Get File Name
// =====================================================

export function getStorageFileName(path) {

    if (!path) {
        return "";
    }

    const cleanPath =
        String(path)
            .split("?")[0]
            .replace(/\\/g, "/");

    const parts = cleanPath.split("/");

    return parts[parts.length - 1] || "";
}


// =====================================================
// Safe File Name
// =====================================================

function createSafeFileName(fileName) {

    const originalName =
        String(fileName || "document.pdf");

    const lastDot =
        originalName.lastIndexOf(".");

    const extension =
        lastDot >= 0
            ? originalName.slice(lastDot).toLowerCase()
            : ".pdf";

    const nameWithoutExtension =
        lastDot >= 0
            ? originalName.slice(0, lastDot)
            : originalName;

    const safeName =
        nameWithoutExtension
            .trim()
            .replace(/[^a-zA-Z0-9_-]+/g, "-")
            .replace(/-+/g, "-")
            .replace(/^-|-$/g, "")
            .slice(0, 80);

    return `${safeName || "document"}${extension}`;
}


// =====================================================
// Create Unique Public ID
// =====================================================

function createCloudinaryPublicId(
    file,
    folder = "documents"
) {

    const safeName =
        createSafeFileName(file.name)
            .replace(/\.pdf$/i, "");

    const uniqueId =
        typeof crypto !== "undefined" &&
        typeof crypto.randomUUID === "function"
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random()
                  .toString(36)
                  .slice(2, 10)}`;

    const safeFolder =
        String(folder || "documents")
            .replace(/\\/g, "/")
            .replace(/^\/+|\/+$/g, "")
            .replace(/[^a-zA-Z0-9/_-]+/g, "-");

    return `${safeFolder}/${uniqueId}-${safeName}`;
}


// =====================================================
// Upload PDF to Cloudinary
// =====================================================

export async function uploadPdf(
    file,
    folder = "documents"
) {

    if (!file) {
        throw new Error(
            "Please select a PDF file."
        );
    }


    // -------------------------------------------------
    // Validate PDF
    // -------------------------------------------------

    if (!isPdfFile(file)) {

        throw new Error(
            "Only PDF files are allowed."
        );
    }


    // -------------------------------------------------
    // 50 MB maximum
    // -------------------------------------------------

    const maxSize =
        50 * 1024 * 1024;

    if (file.size > maxSize) {

        throw new Error(
            "PDF file size must be 50 MB or less."
        );
    }


    // -------------------------------------------------
    // Create Cloudinary public ID
    // -------------------------------------------------

    const publicId =
        createCloudinaryPublicId(
            file,
            folder
        );


    // -------------------------------------------------
    // FormData
    // -------------------------------------------------

    const formData =
        new FormData();

    formData.append(
        "file",
        file
    );

    formData.append(
        "upload_preset",
        CLOUDINARY_UPLOAD_PRESET
    );

    formData.append(
        "public_id",
        publicId
    );


    // -------------------------------------------------
    // Upload to Cloudinary
    // -------------------------------------------------

    let response;

    try {

        response =
            await fetch(
                CLOUDINARY_UPLOAD_URL,
                {
                    method: "POST",
                    body: formData
                }
            );

    } catch (error) {

        console.error(
            "Cloudinary network error:",
            error
        );

        throw new Error(
            "Could not connect to Cloudinary."
        );
    }


    // -------------------------------------------------
    // Read response
    // -------------------------------------------------

    let result = null;

    try {

        result =
            await response.json();

    } catch (error) {

        console.error(
            "Cloudinary response error:",
            error
        );
    }


    // -------------------------------------------------
    // Handle upload error
    // -------------------------------------------------

    if (!response.ok) {

        console.error(
            "Cloudinary upload error:",
            result
        );

        const message =
            result?.error?.message ||
            "PDF upload to Cloudinary failed.";

        throw new Error(message);
    }


    // -------------------------------------------------
    // Validate Cloudinary response
    // -------------------------------------------------

    if (!result?.secure_url) {

        console.error(
            "Invalid Cloudinary response:",
            result
        );

        throw new Error(
            "Cloudinary did not return a PDF URL."
        );
    }


    // -------------------------------------------------
    // Remove missing marker
    // -------------------------------------------------

    missingPdfPaths.delete(
        result.public_id
    );


    // -------------------------------------------------
    // Return data for Supabase
    // -------------------------------------------------

    return {

        // Cloudinary URL
        file_url:
            result.secure_url,

        // Cloudinary public ID
        cloudinary_public_id:
            result.public_id,

        // Original file name
        file_name:
            file.name,

        // Resource type
        resource_type:
            result.resource_type || "raw",

        // Cloudinary version
        version:
            result.version || null
    };
}


// =====================================================
// Optional PDF Upload
// =====================================================

export async function uploadOptionalPdf(
    file,
    folder = "documents"
) {

    if (!file) {
        return null;
    }

    return await uploadPdf(
        file,
        folder
    );
}


// =====================================================
// Remove Cloudinary File
// =====================================================
//
// IMPORTANT:
// Cloudinary deletion requires authenticated
// server-side API access.
//
// API Secret must NEVER be placed in frontend code.
//
// Therefore this function does not attempt to
// delete Cloudinary assets directly from the browser.
// =====================================================

export async function removeStorage(
    publicId,
    resourceType = "raw"
) {
    if (!publicId) {
        return {
            success: false,
            skipped: true
        };
    }

    try {
        console.log(
            "Requesting secure Cloudinary deletion:",
            publicId
        );

        const {
            data,
            error
        } = await supabase.functions.invoke(
            "delete-cloudinary",
            {
                body: {
                    publicId: String(publicId),
                    resourceType:
                        resourceType || "raw"
                }
            }
        );

        if (error) {
            console.error(
                "Cloudinary secure deletion error:",
                error
            );

            throw error;
        }

        if (
            !data ||
            data.success !== true
        ) {
            throw new Error(
                data?.error ||
                "Cloudinary asset deletion failed."
            );
        }

        console.log(
            "Cloudinary asset deleted successfully:",
            publicId
        );

        return data;

    } catch (error) {

        console.error(
            "Cloudinary deletion failed:",
            error
        );

        throw error;
    }
}


// =====================================================
// Prepare PDF Replacement
// =====================================================
//
// 1. Upload new PDF
// 2. Update Supabase record
// 3. Old Cloudinary file can later be deleted
//    through a secure backend function.
// =====================================================

export async function preparePdfReplacement(
    newFile,
    folder = "documents"
) {

    if (!newFile) {

        return {

            file_url: null,

            cloudinary_public_id: null,

            oldCloudinaryPublicId: null,

            markCommitted() {},

            async cleanup() {}
        };
    }


    const uploaded =
        await uploadPdf(
            newFile,
            folder
        );


    let committed = false;


    return {

        file_url:
            uploaded.file_url,

        cloudinary_public_id:
            uploaded.cloudinary_public_id,

        oldCloudinaryPublicId:
            null,


        markCommitted() {

            committed = true;
        },


        async cleanup() {

            // Do not delete anything after
            // database update is committed.

            if (committed) {
                return;
            }

            // Cloudinary deletion cannot safely
            // be performed from frontend.

            console.warn(
                "New Cloudinary asset needs backend cleanup:",
                uploaded.cloudinary_public_id
            );
        }
    };
}
