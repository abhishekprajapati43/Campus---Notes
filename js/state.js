
// =====================================================

// TechCampus_Hub

// state.js

// =====================================================



// Current authentication/signup mode

export let isSignupMode = false;



// Currently logged-in student's profile

export let currentStudent = null;



// Current user's role: "student" or "admin"

export let currentUserRole = null;





export function getSignupMode() {

    return isSignupMode;

}



// -----------------------------------------------------

// Signup / Login mode

// -----------------------------------------------------



export function setSignupMode(value) {

    isSignupMode = Boolean(value);

}





// -----------------------------------------------------

// Current Student

// -----------------------------------------------------



export function setCurrentStudent(student) {

    currentStudent = student || null;

}





// -----------------------------------------------------

// Current User Role

// -----------------------------------------------------



export function setCurrentUserRole(role) {

    currentUserRole = role || null;

}





// -----------------------------------------------------

// Clear User State

// -----------------------------------------------------



export function clearUserState() {

    currentStudent = null;

    currentUserRole = null;

    isSignupMode = false;

}