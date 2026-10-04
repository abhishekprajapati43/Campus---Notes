
// =====================================================

// TechCampus_Hub

// render.js

// =====================================================



import {

    $,

    escapeHtml,

    formatDate,

    formatTime

} from "./helpers.js";



import {

    getFilteredNotes,

    getFilteredTnp,

    getFilteredPyq,

    getFilteredTimetable

} from "./search.js";



import {

    getPdfUrl

} from "./storage.js";



import {

    currentUserRole

} from "./state.js";





// =====================================================

// Role Helper

// =====================================================



function isAdmin() {

    return currentUserRole === "admin";

}





// =====================================================

// Get Resource Container

// =====================================================



function getContainer() {

    return $("notesContainer");

}





// =====================================================

// Empty Message

// =====================================================



function emptyMessage(message) {

    return `

        <div class="col-span-full">

            <div class="rounded-2xl border border-dashed

                        border-gray-300 bg-white p-10

                        text-center shadow-sm">



                <div class="mx-auto mb-4 flex h-14 w-14

                            items-center justify-center

                            rounded-full bg-gray-100">



                    <i class="fa-solid fa-folder-open

                              text-xl text-gray-400"></i>

                </div>



                <h3 class="text-lg font-semibold

                           text-gray-800">

                    ${escapeHtml(message)}

                </h3>



                <p class="mt-2 text-sm text-gray-500">

                    Try changing the filters or search

                    for another resource.

                </p>

            </div>

        </div>

    `;

}





// =====================================================

// Get File URL

// =====================================================

//

// Private Supabase storage requires a signed URL.

// file_url is supported for backward compatibility,

// but storage_path is preferred.

// =====================================================



async function getFileUrl(item) {

    if (!item) {

        return "";

    }



    // Existing public/external URL

    if (item.file_url) {

        return item.file_url;

    }



    // Private Supabase Storage

    if (item.storage_path) {

        return await getPdfUrl(item);

    }



    return "";

}





// =====================================================

// PDF Action Buttons

// =====================================================



function actionButtons(

    fileUrl,

    item,

    category

) {

    const buttons = [];



    if (fileUrl) {

        buttons.push(`

            <a

                href="${escapeHtml(fileUrl)}"

                target="_blank"

                rel="noopener noreferrer"

                class="inline-flex items-center gap-2

                       rounded-lg bg-gray-100 px-3 py-2

                       text-sm font-medium text-gray-700

                       transition hover:bg-gray-200"

            >

                <i class="fa-regular fa-eye"></i>

                View

            </a>



            <a

                href="${escapeHtml(fileUrl)}"

                download

                target="_blank"

                rel="noopener noreferrer"

                class="inline-flex items-center gap-2

                       rounded-lg bg-gray-100 px-3 py-2

                       text-sm font-medium text-gray-700

                       transition hover:bg-gray-200"

            >

                <i class="fa-solid fa-download"></i>

                Download

            </a>

        `);

    }



    // -------------------------------------------------

    // Admin actions

    // -------------------------------------------------



    if (isAdmin() && item?.id !== undefined) {

        buttons.push(`

            <button

                type="button"

                class="edit-resource-btn

                       inline-flex items-center gap-2

                       rounded-lg bg-blue-50 px-3 py-2

                       text-sm font-medium text-blue-700

                       transition hover:bg-blue-100"

                data-resource-category="${escapeHtml(category)}"

                data-resource-id="${escapeHtml(item.id)}"

            >

                <i class="fa-solid fa-pen"></i>

                Edit

            </button>



            <button

                type="button"

                class="delete-resource-btn

                       inline-flex items-center gap-2

                       rounded-lg bg-red-50 px-3 py-2

                       text-sm font-medium text-red-700

                       transition hover:bg-red-100"

                data-resource-category="${escapeHtml(category)}"

                data-resource-id="${escapeHtml(item.id)}"

            >

                <i class="fa-solid fa-trash"></i>

                Delete

            </button>

        `);

    }



    if (!buttons.length) {

        return "";

    }



    return `

        <div class="mt-5 flex flex-wrap gap-2">

            ${buttons.join("")}

        </div>

    `;

}





// =====================================================

// Base Resource Card

// =====================================================



function card({

    icon = "fa-file-pdf",

    iconClass = "bg-red-50 text-red-600",

    title = "Untitled",

    subtitle = "",

    meta = "",

    description = "",

    fileUrl = "",

    item = null,

    category = ""

}) {

    return `

        <article

            class="resource-card group rounded-2xl

                   border border-gray-200 bg-white

                   p-5 shadow-sm transition

                   hover:-translate-y-0.5

                   hover:shadow-md"

        >



            <div class="flex items-start justify-between

                        gap-4">



                <div class="flex min-w-0 items-start

                            gap-4">



                    <div class="flex h-12 w-12 shrink-0

                                items-center justify-center

                                rounded-xl ${iconClass}">



                        <i class="fa-solid ${icon}"></i>

                    </div>



                    <div class="min-w-0">



                        <h3 class="truncate text-base

                                   font-semibold text-gray-900"

                            title="${escapeHtml(title)}">



                            ${escapeHtml(

                                title || "Untitled"

                            )}

                        </h3>



                        ${

                            subtitle

                                ? `

                                    <p class="mt-1 text-sm

                                              font-medium

                                              text-gray-600">



                                        ${escapeHtml(

                                            subtitle

                                        )}

                                    </p>

                                  `

                                : ""

                        }

                    </div>

                </div>



                ${

                    fileUrl

                        ? `

                            <span

                                class="shrink-0 rounded-full

                                       bg-red-50 px-2.5 py-1

                                       text-xs font-medium

                                       text-red-600"

                            >

                                PDF

                            </span>

                          `

                        : ""

                }

            </div>





            ${

                meta

                    ? `

                        <div class="mt-4 flex flex-wrap

                                    gap-2 text-xs">



                            ${meta}

                        </div>

                      `

                    : ""

            }





            ${

                description

                    ? `

                        <p class="mt-4 line-clamp-2

                                  text-sm text-gray-500">



                            ${escapeHtml(

                                description

                            )}

                        </p>

                      `

                    : ""

            }





            ${actionButtons(

                fileUrl,

                item,

                category

            )}

        </article>

    `;

}





// =====================================================

// Meta Badge

// =====================================================



function metaBadge(

    label,

    value,

    className = "bg-gray-100 text-gray-600"

) {

    if (

        value === null ||

        value === undefined ||

        String(value).trim() === ""

    ) {

        return "";

    }



    return `

        <span

            class="inline-flex items-center gap-1

                   rounded-full px-2.5 py-1

                   ${className}"

        >

            <span class="font-medium">

                ${escapeHtml(label)}:

            </span>



            <span>

                ${escapeHtml(value)}

            </span>

        </span>

    `;

}





// =====================================================

// Render Notes Card

// =====================================================



async function renderNoteCard(note) {

    const fileUrl =

        await getFileUrl(note);



    const meta = [

        metaBadge(

            "Course",

            note.course

        ),



        metaBadge(

            "Branch",

            note.branch

        ),



        metaBadge(

            "Year",

            note.year

        ),



        metaBadge(

            "Semester",

            note.semester

        ),



        metaBadge(

            "Subject",

            note.subject,

            "bg-blue-50 text-blue-700"

        )

    ].join("");



    return card({

        icon: "fa-book-open",

        iconClass:

            "bg-blue-50 text-blue-600",



        title:

            note.title ||

            note.subject ||

            "Study Note",



        subtitle:

            note.subject || "",



        meta,



        fileUrl,



        item: note,



        category: "notes"

    });

}





// =====================================================

// Render TNP Card

// =====================================================



async function renderTnpCard(item) {

    const fileUrl =

        await getFileUrl(item);



    const meta = [

        metaBadge(

            "Subject",

            item.subject,

            "bg-purple-50 text-purple-700"

        )

    ].join("");



    return card({

        icon: "fa-briefcase",

        iconClass:

            "bg-purple-50 text-purple-600",



        title:

            item.title ||

            item.subject ||

            "Training & Placement",



        subtitle:

            item.subject || "",



        meta,



        fileUrl,



        item,



        category: "tnp"

    });

}





// =====================================================

// Render PYQ Card

// =====================================================



async function renderPyqCard(item) {

    const fileUrl =

        await getFileUrl(item);



    const meta = [

        metaBadge(

            "Course",

            item.course

        ),



        metaBadge(

            "Year",

            item.year

        ),



        metaBadge(

            "Semester",

            item.semester

        ),



        metaBadge(

            "Subject",

            item.subject,

            "bg-green-50 text-green-700"

        )

    ].join("");



    return card({

        icon: "fa-file-lines",

        iconClass:

            "bg-green-50 text-green-600",



        title:

            item.title ||

            item.subject ||

            "Previous Year Paper",



        subtitle:

            item.subject || "",



        meta,



        fileUrl,



        item,



        category: "pyq"

    });

}





// =====================================================

// Render Timetable Card

// =====================================================



async function renderTimetableCard(item) {

    const fileUrl =

        await getFileUrl(item);



    const examDate =

        formatDate(item.exam_date);



    const startTime =

        formatTime(item.start_time);



    const endTime =

        formatTime(item.end_time);



    const timeText =

        startTime && endTime

            ? `${startTime} - ${endTime}`

            : startTime || endTime;



    const meta = [

        metaBadge(

            "Course",

            item.course

        ),



        metaBadge(

            "Semester",

            item.semester

        ),



        metaBadge(

            "Exam Year",

            item.exam_year

        ),



        metaBadge(

            "Date",

            examDate,

            "bg-orange-50 text-orange-700"

        ),



        metaBadge(

            "Time",

            timeText

        ),



        metaBadge(

            "Type",

            item.exam_type

        ),



        metaBadge(

            "Subject",

            item.subject,

            "bg-indigo-50 text-indigo-700"

        )

    ].join("");



    return card({

        icon: "fa-calendar-days",

        iconClass:

            "bg-orange-50 text-orange-600",



        title:

            item.subject ||

            "Exam Timetable",



        subtitle:

            item.exam_type ||

            "Examination",



        meta,



        fileUrl,



        item,



        category: "timetable"

    });

}





// =====================================================

// Render Notes

// =====================================================



export async function renderNotes(

    items = null

) {

    const container =

        getContainer();



    if (!container) {

        return;

    }



    const data =

        Array.isArray(items)

            ? items

            : getFilteredNotes();



    if (!data.length) {

        container.innerHTML =

            emptyMessage(

                "No notes found"

            );



        return;

    }



    const cards =

        await Promise.all(

            data.map(

                item =>

                    renderNoteCard(item)

            )

        );



    container.innerHTML =

        cards.join("");

}





// =====================================================

// Render TNP

// =====================================================



export async function renderTnp(

    items = null

) {

    const container =

        getContainer();



    if (!container) {

        return;

    }



    const data =

        Array.isArray(items)

            ? items

            : getFilteredTnp();



    if (!data.length) {

        container.innerHTML =

            emptyMessage(

                "No TNP resources found"

            );



        return;

    }



    const cards =

        await Promise.all(

            data.map(

                item =>

                    renderTnpCard(item)

            )

        );



    container.innerHTML =

        cards.join("");

}





// =====================================================

// Render PYQ

// =====================================================



export async function renderPyq(

    items = null

) {

    const container =

        getContainer();



    if (!container) {

        return;

    }



    const data =

        Array.isArray(items)

            ? items

            : getFilteredPyq();



    if (!data.length) {

        container.innerHTML =

            emptyMessage(

                "No previous year papers found"

            );



        return;

    }



    const cards =

        await Promise.all(

            data.map(

                item =>

                    renderPyqCard(item)

            )

        );



    container.innerHTML =

        cards.join("");

}





// =====================================================

// Render Timetable

// =====================================================



export async function renderTimetable(

    items = null

) {

    const container =

        getContainer();



    if (!container) {

        return;

    }



    const data =

        Array.isArray(items)

            ? items

            : getFilteredTimetable();



    if (!data.length) {

        container.innerHTML =

            emptyMessage(

                "No exam timetable found"

            );



        return;

    }



    const cards =

        await Promise.all(

            data.map(

                item =>

                    renderTimetableCard(item)

            )

        );



    container.innerHTML =

        cards.join("");

}





// =====================================================

// Render Current Category

// =====================================================



export async function renderCurrentCategory(

    category = null

) {

    const categorySelect =

        $("searchCategory");



    const selectedCategory =

        category ||

        categorySelect?.value ||

        "notes";



    switch (selectedCategory) {

        case "tnp":

            await renderTnp();

            break;



        case "pyq":

            await renderPyq();

            break;



        case "timetable":

            await renderTimetable();

            break;



        case "notes":

        default:

            await renderNotes();

            break;

    }



    updateCount(

        selectedCategory

    );



    updateCategoryLabel(

        selectedCategory

    );

}





// =====================================================

// Update Count

// =====================================================



export function updateCount(

    category = null

) {

    const countElement =

        $("totalNotesCount");



    if (!countElement) {

        return;

    }



    const categorySelect =

        $("searchCategory");



    const selectedCategory =

        category ||

        categorySelect?.value ||

        "notes";



    let count = 0;



    switch (selectedCategory) {

        case "tnp":

            count =

                getFilteredTnp().length;

            break;



        case "pyq":

            count =

                getFilteredPyq().length;

            break;



        case "timetable":

            count =

                getFilteredTimetable().length;

            break;



        case "notes":

        default:

            count =

                getFilteredNotes().length;

            break;

    }



    countElement.textContent =

        String(count);

}





// =====================================================

// Update Category Label

// =====================================================



export function updateCategoryLabel(

    category = null

) {

    const label =

        $("currentCategoryLabel");



    if (!label) {

        return;

    }



    const categorySelect =

        $("searchCategory");



    const selectedCategory =

        category ||

        categorySelect?.value ||

        "notes";



    const labels = {

        notes: "Study Notes",

        tnp: "Training & Placement",

        pyq: "Previous Year Papers",

        timetable: "Exam Timetable"

    };



    label.textContent =

        labels[selectedCategory] ||

        "Resources";

}
// =====================================================
// Render Everything
// =====================================================

export async function renderAll(

    category = null

) {

    await renderCurrentCategory(

        category

    );

}





// =====================================================

// Setup Render Actions

// =====================================================

//

// Edit/Delete buttons use custom events so render.js

// does not need to directly depend on admin.js.

// This avoids circular imports.

// =====================================================



export function setupRenderActions() {

    const container =

        getContainer();



    if (!container) {

        return;

    }



    if (

        container.dataset.renderActionsReady ===

        "true"

    ) {

        return;

    }



    container.dataset.renderActionsReady =

        "true";



    container.addEventListener(

        "click",

        event => {



            const editButton =

                event.target.closest(

                    ".edit-resource-btn"

                );



            if (editButton) {

                const category =

                    editButton.dataset

                        .resourceCategory;



                const id =

                    editButton.dataset

                        .resourceId;



                window.dispatchEvent(

                    new CustomEvent(

                        "campusnotes:edit-resource",

                        {

                            detail: {

                                category,

                                id

                            }

                        }

                    )

                );



                return;

            }





            const deleteButton =

                event.target.closest(

                    ".delete-resource-btn"

                );



            if (deleteButton) {

                const category =

                    deleteButton.dataset

                        .resourceCategory;



                const id =

                    deleteButton.dataset

                        .resourceId;



                window.dispatchEvent(

                    new CustomEvent(

                        "campusnotes:delete-resource",

                        {

                            detail: {

                                category,

                                id

                            }

                        }

                    )

                );

            }

        }

    );

}


