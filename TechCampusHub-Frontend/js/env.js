
// =====================================================
// TechCampus_Hub
// env.js  —  runtime environment config
// =====================================================

// Loaded as a CLASSIC <script> BEFORE the app modules so that
// js/config.js can read window.__API_BASE_URL__ when it evaluates.
//
// >>> DEPLOYMENT: change ONLY the line below to your production <<<
// >>> backend origin (no trailing slash), e.g. Railway:          <<<
//
//     window.__API_BASE_URL__ = "https://your-service.up.railway.app";
//
// Local development (Live Server) keeps localhost:8080.

window.__API_BASE_URL__ = "http://localhost:8080";
