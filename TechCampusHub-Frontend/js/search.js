
// =====================================================

// TechCampus_Hub

// search.js

// =====================================================



import {

    $,

    escapeHtml,

    populateCourses,

    populateBranches,

    populateYears,

    populateSemesters

} from "./helpers.js";



import {

    notes,

    tnpItems,

    pyqItems,

    timetableItems

} from "./data.js";



import {

    currentStudent,

    currentUserRole

} from "./state.js";





// =====================================================

// Basic Helpers

// =====================================================



function normalize(value) {

    return String(value ?? "")

        .trim()

        .toLowerCase();

}





function sameValue(a, b) {

    return normalize(a) === normalize(b);

}





function isAll(value) {

    const normalized = normalize(value);



    return (

        normalized === "" ||

        normalized === "all" ||

        normalized === "all semesters" ||

        normalized === "all subjects"

    );

}





function isAdmin() {

    return currentUserRole === "admin";

}





function getStudent() {

    return currentStudent || null;

}





function getSearchValue(id) {

    const element = $(id);



    return element

        ? String(element.value || "").trim()

        : "";

}





// =====================================================

// Global Search

// =====================================================



function matchesGlobalSearch(item, fields, query) {

    if (!query) {

        return true;

    }



    const target = normalize(query);



    return fields.some(field => {

        const value = item?.[field];



        return normalize(value).includes(target);

    });

}





// =====================================================

// Notes Filtering

// =====================================================



export function getFilteredNotes() {

    const searchText =

        getSearchValue("searchNotes");



    const semester =

        getSearchValue("filterSemester");



    const subject =

        getSearchValue("searchSubject");



    const course =

        getSearchValue("searchCourse");



    const branch =

        getSearchValue("searchBranch");



    const year =

        getSearchValue("searchYear");



    return notes.filter(note => {



        // -------------------------------------------------

        // Student

        // -------------------------------------------------



        if (!isAdmin()) {

            const student = getStudent();



            if (!student) {

                return false;

            }



            // Course is automatically taken from profile

            if (!sameValue(

                note.course,

                student.course

            )) {

                return false;

            }



            // Branch is automatically taken from profile

            if (!sameValue(

                note.branch,

                student.branch

            )) {

                return false;

            }



            // Semester is optional

            if (

                !isAll(semester) &&

                !sameValue(

                    note.semester,

                    semester

                )

            ) {

                return false;

            }



            // Subject is optional

            if (

                !isAll(subject) &&

                !sameValue(

                    note.subject,

                    subject

                )

            ) {

                return false;

            }

        }



        // -------------------------------------------------

        // Admin

        // -------------------------------------------------



        else {

            if (

                !isAll(course) &&

                !sameValue(

                    note.course,

                    course

                )

            ) {

                return false;

            }



            if (

                !isAll(branch) &&

                !sameValue(

                    note.branch,

                    branch

                )

            ) {

                return false;

            }



            if (

                !isAll(year) &&

                !sameValue(

                    note.year,

                    year

                )

            ) {

                return false;

            }



            if (

                !isAll(semester) &&

                !sameValue(

                    note.semester,

                    semester

                )

            ) {

                return false;

            }



            if (

                !isAll(subject) &&

                !sameValue(

                    note.subject,

                    subject

                )

            ) {

                return false;

            }

        }



        return matchesGlobalSearch(

            note,

            [

                "subject",

                "title",

                "course",

                "branch",

                "year",

                "semester"

            ],

            searchText

        );

    });

}





// =====================================================

// TNP Filtering

// =====================================================

//

// TNP is COMMON for MCA and B.Tech.

//

// No Course

// No Branch

// No Year

// No Semester

//

// Only Subject filter is available.

// =====================================================



export function getFilteredTnp() {

    const searchText =

        getSearchValue("searchNotes");



    const subject =

        getSearchValue("tnpSearchSubject");



    return tnpItems.filter(item => {



        if (

            !isAll(subject) &&

            !sameValue(

                item.subject,

                subject

            )

        ) {

            return false;

        }



        return matchesGlobalSearch(

            item,

            [

                "subject",

                "title"

            ],

            searchText

        );

    });

}





// =====================================================

// PYQ Filtering

// =====================================================



export function getFilteredPyq() {

    const searchText =

        getSearchValue("searchNotes");



    const semester =

        getSearchValue("pyqSearchSemester");



    const subject =

        getSearchValue("pyqSearchSubject");



    const course =

        getSearchValue("pyqSearchCourse");



    const year =

        getSearchValue("pyqSearchYear");



    return pyqItems.filter(item => {



        // -------------------------------------------------

        // Student

        // -------------------------------------------------



        if (!isAdmin()) {

            const student = getStudent();



            if (!student) {

                return false;

            }



            // Course comes automatically from profile

            if (!sameValue(

                item.course,

                student.course

            )) {

                return false;

            }



            // Student does NOT filter by previous year

            // unless this requirement changes later.



            if (

                !isAll(semester) &&

                !sameValue(

                    item.semester,

                    semester

                )

            ) {

                return false;

            }



            if (

                !isAll(subject) &&

                !sameValue(

                    item.subject,

                    subject

                )

            ) {

                return false;

            }

        }



        // -------------------------------------------------

        // Admin

        // -------------------------------------------------



        else {

            if (

                !isAll(course) &&

                !sameValue(

                    item.course,

                    course

                )

            ) {

                return false;

            }



            if (

                !isAll(year) &&

                !sameValue(

                    item.year,

                    year

                )

            ) {

                return false;

            }



            if (

                !isAll(semester) &&

                !sameValue(

                    item.semester,

                    semester

                )

            ) {

                return false;

            }



            if (

                !isAll(subject) &&

                !sameValue(

                    item.subject,

                    subject

                )

            ) {

                return false;

            }

        }



        return matchesGlobalSearch(

            item,

            [

                "course",

                "year",

                "semester",

                "subject",

                "title"

            ],

            searchText

        );

    });

}





// =====================================================

// Timetable Filtering

// =====================================================

//

// Branch has NO role.

// Student course is automatic.

// Student can select any semester.

// =====================================================



export function getFilteredTimetable() {

    const searchText =

        getSearchValue("searchNotes");



    const semester =

        getSearchValue(

            "timetableSearchSemester"

        );



    const subject =

        getSearchValue(

            "timetableSearchSubject"

        );



    const course =

        getSearchValue(

            "timetableSearchCourse"

        );



    const examYear =

        getSearchValue(

            "timetableSearchYear"

        );



    return timetableItems.filter(item => {



        // -------------------------------------------------

        // Student

        // -------------------------------------------------



        if (!isAdmin()) {

            const student = getStudent();



            if (!student) {

                return false;

            }



            // Course comes automatically

            if (!sameValue(

                item.course,

                student.course

            )) {

                return false;

            }



            if (

                !isAll(semester) &&

                !sameValue(

                    item.semester,

                    semester

                )

            ) {

                return false;

            }



            if (

                !isAll(examYear) &&

                !sameValue(

                    item.exam_year,

                    examYear

                )

            ) {

                return false;

            }



            if (

                !isAll(subject) &&

                !sameValue(

                    item.subject,

                    subject

                )

            ) {

                return false;

            }

        }



        // -------------------------------------------------

        // Admin

        // -------------------------------------------------



        else {

            if (

                !isAll(course) &&

                !sameValue(

                    item.course,

                    course

                )

            ) {

                return false;

            }



            if (

                !isAll(semester) &&

                !sameValue(

                    item.semester,

                    semester

                )

            ) {

                return false;

            }



            if (

                !isAll(examYear) &&

                !sameValue(

                    item.exam_year,

                    examYear

                )

            ) {

                return false;

            }



            if (

                !isAll(subject) &&

                !sameValue(

                    item.subject,

                    subject

                )

            ) {

                return false;

            }

        }



        return matchesGlobalSearch(

            item,

            [

                "course",

                "semester",

                "exam_year",

                "exam_date",

                "subject",

                "exam_type"

            ],

            searchText

        );

    });

}





// =====================================================

// Unique Subjects

// =====================================================



function getUniqueSubjects(items) {

    const subjects = items

        .map(item =>

            String(item.subject || "").trim()

        )

        .filter(Boolean);



    return [

        ...new Set(subjects)

    ].sort(

        (a, b) =>

            a.localeCompare(b)

    );

}





// =====================================================

// Fill Subject Select

// =====================================================



function fillSubjectSelect(

    selectElement,

    subjects,

    allLabel = "All Subjects"

) {

    if (!selectElement) {

        return;

    }



    const previousValue =

        selectElement.value;



    selectElement.innerHTML = "";



    const allOption =

        document.createElement("option");



    allOption.value = "";

    allOption.textContent = allLabel;



    selectElement.appendChild(

        allOption

    );



    subjects.forEach(subject => {

        const option =

            document.createElement("option");



        option.value = subject;

        option.textContent = subject;



        selectElement.appendChild(

            option

        );

    });



    if (

        subjects.includes(previousValue)

    ) {

        selectElement.value =

            previousValue;

    } else {

        selectElement.value = "";

    }

}





// =====================================================

// Notes Subject Dropdown

// =====================================================



export function updateNotesSubjects() {

    const select =

        $("searchSubject");



    if (!select) {

        return;

    }



    let filtered = notes;



    const course =

        getSearchValue("searchCourse");



    const branch =

        getSearchValue("searchBranch");



    const year =

        getSearchValue("searchYear");



    const semester =

        getSearchValue("filterSemester");



    if (!isAll(course)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.course,

                    course

                )

        );

    }



    if (!isAll(branch)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.branch,

                    branch

                )

        );

    }



    if (!isAll(year)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.year,

                    year

                )

        );

    }



    if (!isAll(semester)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.semester,

                    semester

                )

        );

    }



    fillSubjectSelect(

        select,

        getUniqueSubjects(filtered)

    );

}





// =====================================================

// TNP Subject Dropdown

// =====================================================



export function updateTnpSubjects() {

    const select =

        $("tnpSearchSubject");



    if (!select) {

        return;

    }



    fillSubjectSelect(

        select,

        getUniqueSubjects(tnpItems)

    );

}





// =====================================================

// PYQ Subject Dropdown

// =====================================================



export function updatePyqSubjects() {

    const select =

        $("pyqSearchSubject");



    if (!select) {

        return;

    }



    let filtered = pyqItems;



    const course =

        getSearchValue("pyqSearchCourse");



    const year =

        getSearchValue("pyqSearchYear");



    const semester =

        getSearchValue("pyqSearchSemester");



    if (!isAll(course)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.course,

                    course

                )

        );

    }



    if (!isAll(year)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.year,

                    year

                )

        );

    }



    if (!isAll(semester)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.semester,

                    semester

                )

        );

    }



    fillSubjectSelect(

        select,

        getUniqueSubjects(filtered)

    );

}





// =====================================================

// Timetable Subject Dropdown

// =====================================================



export function updateTimetableSubjects() {

    const select =

        $("timetableSearchSubject");



    if (!select) {

        return;

    }



    let filtered =

        timetableItems;



    const course =

        getSearchValue(

            "timetableSearchCourse"

        );



    const semester =

        getSearchValue(

            "timetableSearchSemester"

        );



    const examYear =

        getSearchValue(

            "timetableSearchYear"

        );



    if (!isAll(course)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.course,

                    course

                )

        );

    }



    if (!isAll(semester)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.semester,

                    semester

                )

        );

    }



    if (!isAll(examYear)) {

        filtered = filtered.filter(

            item =>

                sameValue(

                    item.exam_year,

                    examYear

                )

        );

    }



    fillSubjectSelect(

        select,

        getUniqueSubjects(filtered)

    );

}





// =====================================================

// Update All Search Subjects

// =====================================================



export function updateAllSearchSubjects() {

    updateNotesSubjects();

    updateTnpSubjects();

    updatePyqSubjects();

    updateTimetableSubjects();

}





// =====================================================

// Search UI Visibility

// =====================================================





// =====================================================

// Populate Search Dropdowns

// =====================================================

export function populateSearchDropdowns() {



    const student = getStudent();

    const admin = isAdmin();



    // =================================================

    // NOTES

    // =================================================



    const notesCourse = $("searchCourse");

    const notesBranch = $("searchBranch");

    const notesYear = $("searchYear");

    const notesSemester = $("filterSemester");



    if (notesCourse) {



        if (admin) {

            populateCourses(notesCourse);

        } else if (student) {



            notesCourse.innerHTML = "";



            const option =

                document.createElement("option");



            option.value = student.course;

            option.textContent = student.course;



            notesCourse.appendChild(option);



            notesCourse.value = student.course;

        }

    }











    if (notesBranch) {



        if (admin) {



            populateBranches(

                notesCourse,

                notesBranch

            );



        } else if (student) {



            notesBranch.innerHTML = "";



            const option =

                document.createElement("option");



            option.value = student.branch;

            option.textContent = student.branch;



            notesBranch.appendChild(option);



            notesBranch.value = student.branch;

        }

    }



    if (notesYear) {



        if (admin) {



            populateYears(

                notesYear,

                notesCourse?.value || ""

            );



        } else {



            notesYear.innerHTML =

                '<option value="">All Years</option>';

        }

    }











if (notesSemester) {



    const previousSemester =

        notesSemester.value;



    populateSemesters(

        notesSemester,

        student?.course ||

        notesCourse?.value ||

        "",

        previousSemester

    );



    const allOption =

        document.createElement("option");



    allOption.value = "ALL";

    allOption.textContent = "All Semesters";



    notesSemester.insertBefore(

        allOption,

        notesSemester.firstChild

    );



    if (previousSemester) {

        notesSemester.value =

            previousSemester;

    }

}





    // =================================================

    // PYQ

    // =================================================



    const pyqCourse =

        $("pyqSearchCourse");



    const pyqYear =

        $("pyqSearchYear");



    const pyqSemester =

        $("pyqSearchSemester");



    if (pyqCourse) {



        if (admin) {



            populateCourses(pyqCourse);



        } else if (student) {



            pyqCourse.innerHTML = "";



            const option =

                document.createElement("option");



            option.value = student.course;

            option.textContent = student.course;



            pyqCourse.appendChild(option);



            pyqCourse.value = student.course;

        }

    }



    if (pyqYear) {



        if (admin) {



            pyqYear.innerHTML =

                '<option value="">All Years</option>';



            const years = [

                ...new Set(

                    pyqItems

                        .map(item =>

                            String(item.year || "")

                        )

                        .filter(Boolean)

                )

            ].sort(

                (a, b) =>

                    Number(b) - Number(a)

            );



            years.forEach(year => {



                const option =

                    document.createElement("option");



                option.value = year;

                option.textContent = year;



                pyqYear.appendChild(option);

            });



        } else {



            pyqYear.innerHTML =

                '<option value="">All Years</option>';

        }

    }



if (pyqSemester) {



    const previousSemester =

        pyqSemester.value;



    populateSemesters(

        pyqSemester,

        student?.course ||

        pyqCourse?.value ||

        "",

        previousSemester

    );



    const allOption =

        document.createElement("option");



    allOption.value = "ALL";

    allOption.textContent = "All Semesters";



    pyqSemester.insertBefore(

        allOption,

        pyqSemester.firstChild

    );



    if (previousSemester) {

        pyqSemester.value =

            previousSemester;

    }

}





    // =================================================

    // TIMETABLE

    // =================================================



    const timetableCourse =

        $("timetableSearchCourse");



    const timetableSemester =

        $("timetableSearchSemester");



    const timetableYear =

        $("timetableSearchYear");



    if (timetableCourse) {



        if (admin) {



            populateCourses(timetableCourse);



        } else if (student) {



            timetableCourse.innerHTML = "";



            const option =

                document.createElement("option");



            option.value = student.course;

            option.textContent = student.course;



            timetableCourse.appendChild(option);



            timetableCourse.value = student.course;

        }

    }



if (timetableSemester) {



    const previousSemester =

        timetableSemester.value;



    populateSemesters(

        timetableSemester,

        student?.course ||

        timetableCourse?.value ||

        "",

        previousSemester

    );



    const allOption =

        document.createElement("option");



    allOption.value = "ALL";

    allOption.textContent = "All Semesters";



    timetableSemester.insertBefore(

        allOption,

        timetableSemester.firstChild

    );



    if (previousSemester) {

        timetableSemester.value =

            previousSemester;

    }

}









    if (timetableYear) {



        timetableYear.innerHTML =

            '<option value="">All Exam Years</option>';



        const years = [

            ...new Set(

                timetableItems

                    .map(item =>

                        String(item.exam_year || "")

                    )

                    .filter(Boolean)

            )

        ].sort(

            (a, b) =>

                Number(b) - Number(a)

        );



        years.forEach(year => {



            const option =

                document.createElement("option");



            option.value = year;

            option.textContent = year;



            timetableYear.appendChild(option);

        });

    }

}





export function updateSearchUI() {

    const categorySelect =

        $("searchCategory");



    if (!categorySelect) {

        return;

    }



    const category =

        categorySelect.value || "notes";



    const notesFilters =

        $("notesSearchFilters");



    const tnpFilters =

        $("tnpSearchFilters");



    const pyqFilters =

        $("pyqSearchFilters");



    const timetableFilters =

        $("timetableSearchFilters");



    // -------------------------------------------------

    // Hide all category filter containers

    // -------------------------------------------------



    [

        notesFilters,

        tnpFilters,

        pyqFilters,

        timetableFilters

    ].forEach(element => {

        if (element) {

            element.classList.add("hidden");

        }

    });



    // -------------------------------------------------

    // Show selected category filters

    // -------------------------------------------------



    if (category === "notes") {

        notesFilters?.classList.remove(

            "hidden"

        );

    }



    if (category === "tnp") {

        tnpFilters?.classList.remove(

            "hidden"

        );

    }



    if (category === "pyq") {

        pyqFilters?.classList.remove(

            "hidden"

        );

    }



    if (category === "timetable") {

        timetableFilters?.classList.remove(

            "hidden"

        );

    }



    updateRoleBasedFilterVisibility(

        category

    );

}





// =====================================================

// Role-Based Search Filter Visibility

// =====================================================



function setFilterVisible(

    id,

    visible

) {

    const element = $(id);



    if (!element) {

        return;

    }



    // The actual filter control may be inside

    // .filter-group, so hide the complete group.

    const parent =

        element.closest(".filter-group");



    const target =

        parent || element;



    target.classList.toggle(

        "hidden",

        !visible

    );

}





function updateRoleBasedFilterVisibility(

    category

) {

    const admin = isAdmin();



    // -------------------------------------------------

    // Notes

    // -------------------------------------------------



    if (category === "notes") {

        setFilterVisible(

            "searchCourse",

            admin

        );



        setFilterVisible(

            "searchBranch",

            admin

        );



        setFilterVisible(

            "searchYear",

            admin

        );



        setFilterVisible(

            "filterSemester",

            true

        );



        setFilterVisible(

            "searchSubject",

            true

        );

    }



    // -------------------------------------------------

    // TNP

    // -------------------------------------------------



    if (category === "tnp") {

        // TNP has only Subject.

        setFilterVisible(

            "tnpSearchCourse",

            false

        );



        setFilterVisible(

            "tnpSearchSubject",

            true

        );

    }



    // -------------------------------------------------

    // PYQ

    // -------------------------------------------------



    if (category === "pyq") {

        setFilterVisible(

            "pyqSearchCourse",

            admin

        );



        setFilterVisible(

            "pyqSearchYear",

            admin

        );



        setFilterVisible(

            "pyqSearchSemester",

            true

        );



        setFilterVisible(

            "pyqSearchSubject",

            true

        );

    }



    // -------------------------------------------------

    // Timetable

    // -------------------------------------------------



    if (category === "timetable") {

        setFilterVisible(

            "timetableSearchCourse",

            admin

        );



        setFilterVisible(

            "timetableSearchSemester",

            true

        );



        setFilterVisible(

            "timetableSearchYear",

            true

        );



        setFilterVisible(

            "timetableSearchSubject",

            true

        );

    }

}





// =====================================================

// Get Search Results

// =====================================================



export function getSearchResults(

    category = "notes"

) {

    switch (category) {

        case "tnp":

            return getFilteredTnp();



        case "pyq":

            return getFilteredPyq();



        case "timetable":

            return getFilteredTimetable();



        case "notes":

        default:

            return getFilteredNotes();

    }

}





// =====================================================

// Refresh Search

// =====================================================



export function refreshSearch(category = null) {



    const selectedCategory =

        category ||

        getSearchValue("searchCategory") ||

        "notes";



    populateSearchDropdowns();



    updateSearchUI();



    updateAllSearchSubjects();



    return getSearchResults(

        selectedCategory

    );

}





// =====================================================

// Generic Filter API

// =====================================================



export function getFilteredData(

    category = "notes"

) {

    return getSearchResults(

        category

    );

}





// =====================================================

// Compatibility Functions

// =====================================================

//

// These functions are kept because events.js may

// import them directly.

//

// They update only the relevant dynamic fields.

// =====================================================



export function updateSearchNotesFields() {

    const course =

        getSearchValue("searchCourse");



    const branchSelect =

        $("searchBranch");



    const yearSelect =

        $("searchYear");



    const semesterSelect =

        $("filterSemester");



    // -------------------------------------------------

    // Branch options

    // -------------------------------------------------



    if (branchSelect) {

        const currentBranch =

            branchSelect.value;



        branchSelect.innerHTML =

            '<option value="">All Branches</option>';



        const branchValues =

            notes

                .filter(item =>

                    isAll(course) ||

                    sameValue(

                        item.course,

                        course

                    )

                )

                .map(item =>

                    String(

                        item.branch || ""

                    ).trim()

                )

                .filter(Boolean);



        [

            ...new Set(branchValues)

        ]

            .sort()

            .forEach(branch => {

                const option =

                    document.createElement(

                        "option"

                    );



                option.value = branch;

                option.textContent = branch;



                branchSelect.appendChild(

                    option

                );

            });



        if (

            [...branchSelect.options]

                .some(

                    option =>

                        option.value ===

                        currentBranch

                )

        ) {

            branchSelect.value =

                currentBranch;

        }

    }



    // -------------------------------------------------

    // Subject

    // -------------------------------------------------



    updateNotesSubjects();



    // Prevent unused-variable warnings in

    // environments that inspect source.

    void yearSelect;

    void semesterSelect;

}





// =====================================================

// Search HTML Helper

// =====================================================

//

// Kept for compatibility if another module needs

// escaped search text.

// =====================================================



export function escapeSearchText(value) {

    return escapeHtml(value);

}