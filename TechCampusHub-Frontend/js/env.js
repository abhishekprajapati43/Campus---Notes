
// =====================================================
// TechCampus_Hub
// env.js  —  runtime environment config
// =====================================================
//
// Loaded as a CLASSIC <script> BEFORE the app modules, so
// js/config.js picks up window.__API_BASE_URL__ when it evaluates.
//
// Resolution order:
//   1. ?api=<url> query param          (one-off override)
//   2. localStorage "campusnotes:apiBase"  (sticky override)
//   3. PROD_API below                  (production default)
//   4. http://localhost:8080           (local dev fallback)
//
// >>> DEPLOYMENT: paste your Railway backend origin here <<<
// >>> (https, no trailing slash) then redeploy the site:  <<<
//
//     const PROD_API = "https://your-service.up.railway.app";
//
// Local Live Server keeps working automatically while PROD_API
// is empty. To force local API while PROD_API is set, open the
// site once with:  ?api=http://localhost:8080
//
(function () {
    const PROD_API = ""; // <-- production backend URL goes here

    let api = "";
    try {
        api = new URLSearchParams(window.location.search).get("api")
            || window.localStorage.getItem("campusnotes:apiBase");
    } catch (e) { /* storage blocked — ignore */ }

    window.__API_BASE_URL__ = api || PROD_API || "http://localhost:8080";
})();
