// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";

import { getAuth } from "firebase/auth";
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
const firebaseConfig = {
  apiKey: "AIzaSyDCPWmq7fYUrtC7SdBLJ4WlEy-EEmNHEuw",
  authDomain: "urbaneye-a40b0.firebaseapp.com",
  projectId: "urbaneye-a40b0",
  storageBucket: "urbaneye-a40b0.firebasestorage.app",
  messagingSenderId: "295906520445",
  appId: "1:295906520445:web:46c34f1b1ee7bf9f1db014"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);


export const auth = getAuth(app);
export default app;