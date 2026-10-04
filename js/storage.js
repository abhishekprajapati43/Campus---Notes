
// =====================================================

// TechCampus_Hub

// storage.js

// =====================================================



import { supabase } from "../supabase.js";

import { STORAGE_BUCKET } from "./config.js";





// =====================================================

// Missing PDF Paths

// =====================================================



export const missingPdfPaths = new Set();





// =====================================================

// Get Signed PDF URL

// =====================================================



export async function getPdfUrl(item) {

    if (!item) {

        return "";

    }



    const storagePath =

        typeof item === "string"

            ? item

            : item.storage_path;



    if (!storagePath) {

        return "";

    }



    if (missingPdfPaths.has(storagePath)) {

        return "";

    }



    const { data, error } = await supabase.storage

        .from(STORAGE_BUCKET)

        .createSignedUrl(storagePath, 60 * 60);



    if (error) {

        console.error(

            "PDF signed URL error:",

            error

        );



        missingPdfPaths.add(storagePath);



        return "";

    }



    return data?.signedUrl || "";

}





// =====================================================

// Check PDF File

// =====================================================



export function isPdfFile(file) {

    if (!file) {

        return false;

    }



    const fileName = String(file.name || "")

        .toLowerCase();



    const fileType = String(file.type || "")

        .toLowerCase();



    return (

        fileType === "application/pdf" ||

        fileName.endsWith(".pdf")

    );

}





// =====================================================

// Get Storage File Name

// =====================================================



export function getStorageFileName(path) {

    if (!path) {

        return "";

    }



    const cleanPath = String(path)

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



    const safeName = nameWithoutExtension

        .trim()

        .replace(/[^a-zA-Z0-9_-]+/g, "-")

        .replace(/-+/g, "-")

        .replace(/^-|-$/g, "")

        .slice(0, 80);



    return `${safeName || "document"}${extension}`;

}





// =====================================================

// Create Unique Storage Path

// =====================================================



function createStoragePath(file, folder = "documents") {

    const safeName = createSafeFileName(file.name);



    const uniqueId =

        typeof crypto !== "undefined" &&

        typeof crypto.randomUUID === "function"

            ? crypto.randomUUID()

            : `${Date.now()}-${Math.random()

                  .toString(36)

                  .slice(2, 10)}`;



    const safeFolder = String(folder || "documents")

        .replace(/\\/g, "/")

        .replace(/^\/+|\/+$/g, "")

        .replace(/[^a-zA-Z0-9/_-]+/g, "-");



    return `${safeFolder}/${uniqueId}-${safeName}`;

}





// =====================================================

// Upload PDF

// =====================================================



export async function uploadPdf(

    file,

    folder = "documents"

) {

    if (!file) {

        throw new Error("Please select a PDF file.");

    }



    if (!isPdfFile(file)) {

        throw new Error(

            "Only PDF files are allowed."

        );

    }



    // 50 MB maximum

    const maxSize =

        50 * 1024 * 1024;



    if (file.size > maxSize) {

        throw new Error(

            "PDF file size must be 50 MB or less."

        );

    }



    const storagePath =

        createStoragePath(file, folder);



    const { error } = await supabase.storage

        .from(STORAGE_BUCKET)

        .upload(storagePath, file, {

            cacheControl: "3600",

            contentType: "application/pdf",

            upsert: false

        });



    if (error) {

        console.error(

            "PDF upload error:",

            error

        );



        throw error;

    }



    // If a previous failed operation marked

    // this path as missing, remove that marker.

    missingPdfPaths.delete(storagePath);



    return {

        storage_path: storagePath,

        file_name: getStorageFileName(storagePath)

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



    return await uploadPdf(file, folder);

}





// =====================================================

// Remove Storage File

// =====================================================



export async function removeStorage(

    storagePath

) {

    if (!storagePath) {

        return true;

    }



    const { error } = await supabase.storage

        .from(STORAGE_BUCKET)

        .remove([storagePath]);



    if (error) {

        console.error(

            "Storage delete error:",

            error

        );



        throw error;

    }



    missingPdfPaths.delete(storagePath);



    return true;

}






















// =====================================================

// Prepare PDF Replacement

// =====================================================

//

// Replacement flow:

//

// 1. Upload new PDF

// 2. Update database with new path

// 3. Delete old PDF

//

// If database update fails, the newly uploaded

// file can be cleaned up using replacement.cleanup().

//

// After DB update succeeds, call:

// replacement.markCommitted()

//

// =====================================================



export async function preparePdfReplacement(

    newFile,

    folder = "documents"

) {

    if (!newFile) {

        return {

            storage_path: null,

            oldStoragePath: null,



            markCommitted() {},



            async cleanup() {}

        };

    }



    const uploaded =

        await uploadPdf(newFile, folder);



    let committed = false;



    return {

        storage_path:

            uploaded.storage_path,



        oldStoragePath: null,



        markCommitted() {

            committed = true;

        },



        async cleanup() {

            // If DB update was already committed,

            // do NOT delete the new file.

            if (committed) {

                return;

            }



            if (!uploaded.storage_path) {

                return;

            }



            try {

                await removeStorage(

                    uploaded.storage_path

                );

            } catch (error) {

                console.error(

                    "Replacement cleanup error:",

                    error

                );

            }

        }

    };

}

