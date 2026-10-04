

// =====================================================
// TechCampus_Hub
// app.js
// =====================================================



import { setupEvents } from "./events.js";

import { initializeApp } from "./ui.js";



// =====================================================

// Start Application

// =====================================================



document.addEventListener(

    "DOMContentLoaded",

    async () => {

        try {

            // Setup all application events first.

            setupEvents();



            // Then initialize authentication,

            // profile and dashboard.

            await initializeApp();



        } catch (error) {

            console.error(

                "Application initialization error:",

                error

            );

        }

    }

);


