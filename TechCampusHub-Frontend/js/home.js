// =====================================================
// TechCampus_Hub
// home.js
//
// Public storefront: renders the course catalog and
// adapts the navigation to the visitor's session.
// =====================================================

import { getSession } from "./auth.js";
import { loadCourses, formatPrice, ratingBadgeHtml } from "./courses.js";
import { getOverallRating } from "./api.js";
import { escapeHtml } from "./helpers.js";
import { initThemeToggle } from "./theme.js";

/**
 * Platform-wide average rating shown above the homepage catalog,
 * e.g. "⭐ 4.8/5 · from 1,240 reviews".
 */
async function renderOverallRating() {
    const box = document.getElementById("overallRatingSummary");

    if (!box) {
        return;
    }

    try {
        const summary = await getOverallRating();
        const count = Number(summary?.count) || 0;
        const average = Number(summary?.average) || 0;

        if (!count) {
            box.innerHTML = `
                <span class="text-sm text-slate-500 font-semibold">
                    <i class="fa-regular fa-star text-amber-400 mr-1"></i>
                    No reviews yet — be the first to rate a course
                </span>
            `;
            return;
        }

        box.innerHTML = `
            <span class="inline-flex items-center gap-2 rounded-full
                         bg-amber-50 border border-amber-200
                         px-4 py-1.5 text-sm font-extrabold text-amber-700"
                  title="Average of ${count} review${count === 1 ? "" : "s"}">
                <i class="fa-solid fa-star text-amber-400"></i>
                ${average.toFixed(1)}/5
                <span class="font-semibold text-amber-600">
                    from ${count.toLocaleString("en-IN")} review${count === 1 ? "" : "s"}
                </span>
            </span>
        `;
    } catch (error) {
        console.error("Overall rating load error:", error);
        box.innerHTML = "";
    }
}

/**
 * Top-corner pill badge for a course card: an explicit badge
 * when the data provides one, otherwise a rating-based
 * "Best Seller" or a "New" tag for recently added courses.
 */
function courseCardBadgeHtml(course) {
    const badgeBase = `absolute -top-3 right-4 rounded-full px-3 py-1
                       text-[11px] font-extrabold shadow`;
    const explicit = typeof course.badge === "string" ? course.badge.trim() : "";

    if (explicit) {
        return `<span class="${badgeBase} bg-blue-600 text-white">${escapeHtml(explicit)}</span>`;
    }

    if (Number(course.rating_avg) >= 4.5 && Number(course.rating_count) > 0) {
        return `<span class="${badgeBase} bg-amber-500 text-white">Best Seller</span>`;
    }

    const createdAt = Date.parse(course.created_at || "");

    if (createdAt && Date.now() - createdAt <= 21 * 24 * 60 * 60 * 1000) {
        return `<span class="${badgeBase} bg-emerald-500 text-white">New</span>`;
    }

    return "";
}

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
            const priceValue = Number(course.price_inr) || 0;
            const mrp = Number(course.mrp_inr) || 0;

            // Discount row only when a compare-at MRP is
            // present and higher than the selling price.
            const discountPercent = mrp > priceValue
                ? Math.round(((mrp - priceValue) / mrp) * 100)
                : 0;

            const priceBlock = discountPercent > 0
                ? `
                    <div class="flex flex-wrap items-center gap-x-2">
                        <p class="text-2xl font-extrabold text-slate-900">
                            ${escapeHtml(price)}
                        </p>
                        <span class="rounded-full bg-amber-100 px-2 py-0.5
                                     text-xs font-extrabold text-amber-700">
                            ${discountPercent}% Off
                        </span>
                        <p class="w-full text-sm text-slate-400 line-through">
                            ${escapeHtml(formatPrice(mrp))}
                        </p>
                    </div>
                `
                : `
                    <p class="text-2xl font-extrabold text-slate-900">
                        ${escapeHtml(price)}
                    </p>
                `;

            const highlights = (course.highlights || [])
                .map(item => `
                    <li class="flex items-start gap-2 text-sm text-slate-600">
                        <i class="fa-solid fa-check text-emerald-500 mt-1"></i>
                        <span>${escapeHtml(item)}</span>
                    </li>
                `)
                .join("");

            return `
                <article class="course-tile card p-6 flex flex-col relative h-full
                                bg-white rounded-3xl border border-slate-200
                                shadow-soft hover:-translate-y-1 transition">
                    ${courseCardBadgeHtml(course)}
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
                    ${ratingBadgeHtml(course)}
                    <p class="mt-2 text-sm text-slate-500 flex-1">
                        ${escapeHtml(course.description || "")}
                    </p>
                    <ul class="mt-4 space-y-2">
                        ${highlights}
                    </ul>
                    <div class="mt-5 flex items-center justify-between gap-3">
                        ${priceBlock}
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
    // Theme toggle in the storefront header (static markup).
    initThemeToggle();

    updateNavForSession();
    renderOverallRating();
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
