// common.js

// Set timeout duration (in milliseconds)
var sessionTimeout = 77 * 60 * 1000; // 77 minutes
var timeout;

// Function to redirect to the login page
function redirectToLogin() {
    window.location.href = '/login';
}

// Function to reset the timeout
function resetTimeout() {
    clearTimeout(timeout);
    timeout = setTimeout(redirectToLogin, sessionTimeout);
}

// Set initial timer
//alert("Setting initial timeout");
timeout = setTimeout(redirectToLogin, sessionTimeout);

// Reset timer on user interactions
window.onload = resetTimeout;
document.onmousemove = resetTimeout;
document.onkeypress = resetTimeout;
document.onclick = resetTimeout;
document.onscroll = resetTimeout;
