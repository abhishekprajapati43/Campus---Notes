// =====================================================
// TechCampus_Hub
// home.js
//
// Public storefront: renders the course catalog and
// adapts the navigation to the visitor's session.
// =====================================================

import { getSession } from "./auth.js";
import { loadCourses, formatPrice } from "./courses.js";
import { escapeHtml } from "./helpers.js";

async function renderCourseCatalog() {
    const grid = document.getElementById("homeCourseGrid");
    const empty = document.getElementById("homeCourseEmpty");

    if (!grid) {
        return;
    }

    try {
        const courses = await loadCourses();

        if (!courses.length) {
            if (empty) {
                empty.classList.remove("hidden");
            }
            return;
        }

        grid.innerHTML = courses.map(course => {
            const price = formatPrice(course.price_inr);

            const highlights = (course.highlights || [])
                .map(item => `
                    <li class="flex items-start gap-2 text-sm text-slate-600">
                        <i class="fa-solid fa-check text-emerald-500 mt-1"></i>
                        <span>${escapeHtml(item)}</span>
                    </li>
                `)
                .join("");

            return `
                <article class="course-tile card p-6 flex flex-col
                                bg-white rounded-3xl border border-slate-200
                                shadow-soft hover:-translate-y-1 transition">
                    <div class="flex items-start justify-between gap-3">
                        <div class="w-12 h-12 rounded-xl bg-blue-50 text-blue-600
                                    flex items-center justify-center text-xl shrink-0">
                            <i class="fa-solid fa-graduation-cap"></i>
                        </div>
                        <span class="rounded-full bg-slate-100 px-3 py-1
                                     text-xs font-bold text-slate-500">
                            One-time
                        </span>
                    </div>
                    <h3 class="mt-4 text-lg font-extrabold text-slate-900">
                        ${escapeHtml(course.title)}
                    </h3>
                    <p class="mt-2 text-sm text-slate-500 flex-1">
                        ${escapeHtml(course.description || "")}
                    </p>
                    <ul class="mt-4 space-y-2">
                        ${highlights}
                    </ul>
                    <div class="mt-5 flex items-center justify-between gap-3">
                        <p class="text-2xl font-extrabold text-slate-900">
                            ${escapeHtml(price)}
                        </p>
                        <a href="./courses.html"
                           class="course-cta inline-flex items-center gap-2 rounded-xl
                                  bg-blue-600 hover:bg-blue-700 text-white
                                  font-bold px-5 py-2.5 transition">
                            View course
                        </a>
                    </div>
                </article>
            `;
        }).join("");
    } catch (error) {
        console.error("Home catalog render error:", error);
    }
}

// Session flag kept synchronous so the click guard below
// can call preventDefault before navigation starts.
let visitorSignedIn = false;

async function updateNavForSession() {
    const guestActions = document.getElementById("guestNavActions");
    const memberActions = document.getElementById("memberNavActions");

    try {
        const { data } = await getSession();

        visitorSignedIn = Boolean(data?.session?.user);

        guestActions?.classList.toggle("hidden", visitorSignedIn);
        memberActions?.classList.toggle("hidden", !visitorSignedIn);
    } catch (error) {
        console.error("Session check error:", error);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    updateNavForSession();
    renderCourseCatalog();

    // Viewing or buying a course requires an account:
    // guests are redirected to the login page.
    document.body.addEventListener("click", event => {
        const cta = event.target.closest("a.course-cta");

        if (!cta || visitorSignedIn) {
            return;
        }

        event.preventDefault();
        window.location.href = "./login.html";
    });
});
