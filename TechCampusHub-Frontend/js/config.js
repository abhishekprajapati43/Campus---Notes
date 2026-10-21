
// =====================================================
// TechCampus_Hub
// config.js
// =====================================================

// Base URL of the Spring Boot backend API.
// Override per environment, e.g.:
// window.__API_BASE_URL__ = "https://your-backend.example.com";
export const API_BASE_URL =
    (typeof window !== "undefined" &&
        window.__API_BASE_URL__) ||
    "http://localhost:8080";

//
// Single source of truth for every course/program the
// portal offers (MCA, B.Tech, BCA). Every course dropdown,
// branch list, year list and semester list in the app is
// generated from this map (see helpers.js populateCourses /
// populateBranches / populateYears / populateSemesters),
// so a new program only has to be added HERE.
//
// The backend stores the label as free text and scopes
// content with a normalised comparison (ProgramFilter),
// so the value below is both the dropdown label and the
// value saved on the student's profile and on uploads.
//
export const courseStructure = {

    MCA: {
        duration: 2,
        semesters: 4,

        branches: [

            {
                value: "CA",
                label: "Computer Applications (CA)"
            }
        ]
    },


    "B.Tech": {
        duration: 4,
        semesters: 8,

        branches: [
            {
                value: "CSE",
                label: "Computer Science & Engineering (CSE)"
            },

            {
                value: "IT",
                label: "Information Technology (IT)"
            },

            {
                value: "AI_ML",
                label: "Artificial Intelligence & Machine Learning"
            },

            {
                value: "ECE",
                label: "Electronics & Communication Engineering (ECE)"
            },

            {
                value: "MECH",
                label: "Mechanical Engineering"
            },

            {
                value: "CIVIL",
                label: "Civil Engineering"
            }
        ]
    },


    BCA: {
        duration: 3,
        semesters: 6,

        branches: [
            {
                value: "CA",
                label: "Computer Applications (CA)"
            }
        ]
    }
};

