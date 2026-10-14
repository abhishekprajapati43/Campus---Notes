// =====================================================

// TechCampus_Hub

// helpers.js

// =====================================================



import { courseStructure } from "./config.js";





// =====================================================

// Basic DOM Helper

// =====================================================



export function $(id) {

    return document.getElementById(id);

}





// =====================================================

// HTML Security

// =====================================================



export function escapeHtml(value) {

    if (value === null || value === undefined) {

        return "";

    }



    return String(value)

        .replace(/&/g, "&amp;")

        .replace(/</g, "&lt;")

        .replace(/>/g, "&gt;")

        .replace(/"/g, "&quot;")

        .replace(/'/g, "&#039;");

}





// =====================================================

// Course Structure

// =====================================================



export function getCourseStructure(course) {

    if (!course) {

        return null;

    }



    return courseStructure[course] || null;

}





// =====================================================

// Populate Branches

// =====================================================



export function populateBranches(

    courseSelect,

    branchSelect,

    selectedBranch = ""

) {

    if (!courseSelect || !branchSelect) {

        return;

    }



    const course = courseSelect.value;

    const structure = getCourseStructure(course);



    branchSelect.innerHTML =

        '<option value="">Select Branch</option>';



    if (!structure || !Array.isArray(structure.branches)) {

        branchSelect.disabled = true;

        return;

    }



    structure.branches.forEach(branch => {

        const option = document.createElement("option");



        option.value = branch.value;

        option.textContent = branch.label;



        branchSelect.appendChild(option);

    });



    branchSelect.disabled = false;



    if (selectedBranch) {

        setSelectValue(branchSelect, selectedBranch);

    }

}





// =====================================================

// Populate Years

// =====================================================



export function populateYears(

    yearSelect,

    course,

    selectedYear = ""

) {

    if (!yearSelect) {

        return;

    }



    yearSelect.innerHTML =

        '<option value="">Select Year</option>';



    const structure = getCourseStructure(course);



    if (!structure) {

        yearSelect.disabled = true;

        return;

    }



    const duration = Number(structure.duration || 0);



    for (let year = 1; year <= duration; year++) {

        const option = document.createElement("option");



        option.value = String(year);

        option.textContent = `Year ${year}`;



        yearSelect.appendChild(option);

    }



    yearSelect.disabled = false;



    if (selectedYear !== "") {

        setSelectValue(yearSelect, selectedYear);

    }

}



export function populateSemesters(

    semesterSelect,

    course,

    selectedSemester = ""

) {

    if (!semesterSelect) {

        return;

    }



    semesterSelect.innerHTML =

        '<option value="" selected disabled hidden>Select Semester</option>';



    const structure = getCourseStructure(course);



    if (!structure) {

        semesterSelect.disabled = true;

        return;

    }



    const totalSemesters =

        Number(structure.semesters || 0);



    for (

        let semester = 1;

        semester <= totalSemesters;

        semester++

    ) {

        const option =

            document.createElement("option");



        option.value = String(semester);

        option.textContent =

            `Semester ${semester}`;



        semesterSelect.appendChild(option);

    }



    semesterSelect.disabled = false;



    if (selectedSemester !== "") {

        setSelectValue(

            semesterSelect,

            selectedSemester

        );

    }

}







// =====================================================

// Populate Semesters

// =====================================================





// =====================================================

// Populate Courses

// =====================================================



export function populateCourses(

    courseSelect,

    selectedCourse = ""

) {

    if (!courseSelect) {

        return;

    }



    courseSelect.innerHTML =

        '<option value="">Select Course</option>';



    Object.keys(courseStructure).forEach(course => {

        const option = document.createElement("option");



        option.value = course;

        option.textContent = course;



        courseSelect.appendChild(option);

    });



    if (selectedCourse !== "") {

        setSelectValue(courseSelect, selectedCourse);

    }

}







// =====================================================

// Select Value Helper

// =====================================================



export function setSelectValue(selectElement, value) {

    if (!selectElement) {

        return;

    }



    const targetValue = String(value ?? "");



    const optionExists = Array.from(

        selectElement.options

    ).some(option => option.value === targetValue);



    if (optionExists) {

        selectElement.value = targetValue;

    }

}





// =====================================================

// Show / Hide

// =====================================================



export function showElement(element) {

    if (!element) {

        return;

    }



    element.classList.remove("hidden");

}





export function hideElement(element) {

    if (!element) {

        return;

    }



    element.classList.add("hidden");

}





// =====================================================

// Clear Input

// =====================================================



export function clearInput(input) {

    if (!input) {

        return;

    }



    input.value = "";

}





// =====================================================

// Registration Fields

// =====================================================



export function updateRegistrationFields() {

    const courseSelect = $("regCourse");

    const branchSelect = $("regBranch");

    const yearSelect = $("regYear");

    const semesterSelect = $("regSemester");



    if (!courseSelect) {

        return;

    }



    const course = courseSelect.value;



    if (branchSelect) {

        populateBranches(courseSelect, branchSelect);

    }



    if (yearSelect) {

        populateYears(yearSelect, course);

    }



    if (semesterSelect) {

        populateSemesters(semesterSelect, course);

    }

}





// =====================================================

// Notes Admin Fields

// =====================================================



export function updateNoteFields() {

    const courseSelect = $("noteCourse");

    const branchSelect = $("noteBranch");

    const yearSelect = $("noteYear");

    const semesterSelect = $("noteSemester");



    if (!courseSelect) {

        return;

    }



    const course = courseSelect.value;



    if (branchSelect) {

        populateBranches(courseSelect, branchSelect);

    }



    if (yearSelect) {

        populateYears(yearSelect, course);

    }



    if (semesterSelect) {

        populateSemesters(semesterSelect, course);

    }

}





// =====================================================

// PYQ Admin Fields

// =====================================================



export function updatePyqFields() {

    const courseSelect = $("pyqCourse");

    const yearSelect = $("pyqYear");

    const semesterSelect = $("pyqSemester");



    if (!courseSelect) {

        return;

    }



    const course = courseSelect.value;



    if (yearSelect) {

        populateYears(yearSelect, course);

    }



    if (semesterSelect) {

        populateSemesters(semesterSelect, course);

    }

}





// =====================================================

// Timetable Admin Fields

// =====================================================



export function updateTimetableFields() {

    const courseSelect = $("timetableCourse");

    const semesterSelect = $("timetableSemester");



    if (!courseSelect) {

        return;

    }



    const course = courseSelect.value;



    if (semesterSelect) {

        populateSemesters(semesterSelect, course);

    }

}





// =====================================================

// Last 5 Exam Years

// =====================================================



export function populateLast5Years(

    selectElement,

    selectedYear = ""

) {

    if (!selectElement) {

        return;

    }



    const currentYear = new Date().getFullYear();



    selectElement.innerHTML =

        '<option value="">Select Year</option>';



    for (let i = 0; i < 5; i++) {

        const year = currentYear - i;



        const option = document.createElement("option");



        option.value = String(year);

        option.textContent = String(year);



        selectElement.appendChild(option);

    }



    if (selectedYear !== "") {

        setSelectValue(selectElement, selectedYear);

    }

}





// =====================================================

// PYQ Search Fields

// =====================================================



export function updatePyqSearchFields() {

    const courseSelect = $("pyqSearchCourse");

    const semesterSelect = $("pyqSearchSemester");



    if (!courseSelect) {

        return;

    }



    const course = courseSelect.value;



    if (semesterSelect) {

        populateSemesters(semesterSelect, course);

    }

}





// =====================================================

// Timetable Search Fields

// =====================================================



export function updateTimetableSearchFields() {

    const courseSelect = $("timetableSearchCourse");

    const semesterSelect = $("timetableSearchSemester");



    if (!courseSelect) {

        return;

    }



    const course = courseSelect.value;



    if (semesterSelect) {

        populateSemesters(semesterSelect, course);

    }

}





// =====================================================

// Date Formatting

// =====================================================



export function formatDate(value) {

    if (!value) {

        return "";

    }



    const date = new Date(value);



    if (Number.isNaN(date.getTime())) {

        return String(value);

    }



    return date.toLocaleDateString("en-IN", {

        day: "2-digit",

        month: "short",

        year: "numeric"

    });

}





// =====================================================

// Time Formatting

// =====================================================



export function formatTime(value) {

    if (!value) {

        return "";

    }



    const parts = String(value).split(":");



    if (parts.length < 2) {

        return String(value);

    }



    const hours = Number(parts[0]);

    const minutes = Number(parts[1]);



    if (

        Number.isNaN(hours) ||

        Number.isNaN(minutes)

    ) {

        return String(value);

    }



    const date = new Date();



    date.setHours(hours, minutes, 0, 0);



    return date.toLocaleTimeString("en-IN", {

        hour: "2-digit",

        minute: "2-digit",

        hour12: true

    });

}

