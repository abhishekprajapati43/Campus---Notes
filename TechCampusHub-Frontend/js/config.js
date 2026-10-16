
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

export const ADMIN_EMAIL = "admin@college.com";

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
    }
};

