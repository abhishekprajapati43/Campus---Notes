
// =====================================================

// TechCampus_Hub

// data.js

// =====================================================



import { supabase } from "../supabase.js";





// =====================================================

// Application Data

// =====================================================



export let notes = [];

export let tnpItems = [];

export let pyqItems = [];

export let timetableItems = [];

export let students = [];





// =====================================================

// Load Notes

// =====================================================



export async function loadNotes() {

    const {

        data,

        error

    } = await supabase

        .from("notes")

        .select("*")

        .order("created_at", {

            ascending: false

        });



    if (error) {

        console.error(

            "Notes loading error:",

            error

        );



        notes = [];



        return notes;

    }



    notes = data || [];



    return notes;

}





// =====================================================

// Load TNP

// =====================================================



export async function loadTnp() {

    const {

        data,

        error

    } = await supabase

        .from("tnp")

        .select("*")

        .order("created_at", {

            ascending: false

        });



    if (error) {

        console.error(

            "TNP loading error:",

            error

        );



        tnpItems = [];



        return tnpItems;

    }



    tnpItems = data || [];



    return tnpItems;

}





// =====================================================

// Load Previous Year Papers

// =====================================================



export async function loadPyq() {

    const {

        data,

        error

    } = await supabase

        .from("previousYearPapers")

        .select("*")

        .order("created_at", {

            ascending: false

        });



    if (error) {

        console.error(

            "PYQ loading error:",

            error

        );



        pyqItems = [];



        return pyqItems;

    }



    pyqItems = data || [];



    return pyqItems;

}





// =====================================================

// Load Exam Timetable

// =====================================================



export async function loadTimetable() {

    const {

        data,

        error

    } = await supabase

        .from("timetable")

        .select("*")

        .order("exam_date", {

            ascending: true

        })

        .order("start_time", {

            ascending: true

        });



    if (error) {

        console.error(

            "Timetable loading error:",

            error

        );



        timetableItems = [];



        return timetableItems;

    }



    timetableItems = data || [];



    return timetableItems;

}





// =====================================================

// Load Students

// =====================================================

//

// IMPORTANT:

// Passwords are NEVER selected here.

//

// RLS on the students table must ensure that:

// - Student can read only their own profile.

// - Admin can read student profiles as required.

// =====================================================



export async function loadStudents() {

    const {

        data,

        error

    } = await supabase

        .from("students")

        .select(

            "id,email,name,course,branch,year,semester,role,created_at"

        )

        .order("created_at", {

            ascending: false

        });



    if (error) {

        console.error(

            "Students loading error:",

            error

        );



        students = [];



        return students;

    }



    students = data || [];



    return students;

}





// =====================================================

// Load All Data

// =====================================================



export async function loadAllData(

    includeStudents = false

) {

    const requests = [

        loadNotes(),

        loadTnp(),

        loadPyq(),

        loadTimetable()

    ];



    if (includeStudents) {

        requests.push(loadStudents());

    }



    await Promise.all(requests);



    return {

        notes,

        tnpItems,

        pyqItems,

        timetableItems,

        students

    };

}





// =====================================================

// Reload Students

// =====================================================



export async function reloadStudents() {

    return await loadStudents();

}





// =====================================================

// Find Student By ID

// =====================================================



export function findStudentById(id) {

    if (!id) {

        return null;

    }



    return (

        students.find(

            student =>

                String(student.id) === String(id)

        ) || null

    );

}





// =====================================================

// Find Student By Email

// =====================================================



export function findStudentByEmail(email) {

    if (!email) {

        return null;

    }



    const target = String(email)

        .trim()

        .toLowerCase();



    return (

        students.find(

            student =>

                String(student.email || "")

                    .trim()

                    .toLowerCase() === target

        ) || null

    );

}


