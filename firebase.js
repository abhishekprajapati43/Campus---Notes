import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { getStorage } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-storage.js";
import { getFirestore } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const firebaseConfig = {
    apiKey: "AIzaSyDEiidmbJ6RDJbcVNjuYwjiHSU9qHP4iYE",
    authDomain: "college-notes-12c6f.firebaseapp.com",
    projectId: "college-notes-12c6f",
    storageBucket: "college-notes-12c6f.firebasestorage.app",
    messagingSenderId: "896404221357",
    appId: "1:896404221357:web:d1bb1f3b42636bf9724524",
    measurementId: "G-7L4Q6S8JBC"
};

const app = initializeApp(firebaseConfig);

export const storage = getStorage(app);
export const db = getFirestore(app);