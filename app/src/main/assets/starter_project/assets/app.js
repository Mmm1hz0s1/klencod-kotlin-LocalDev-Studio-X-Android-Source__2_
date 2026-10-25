document.addEventListener("DOMContentLoaded", function () {
    var el = document.getElementById("js-status");
    if (el) {
        el.textContent = "JavaScript Engine Active — Loaded /assets/app.js at " + new Date().toISOString();
        el.style.color = "#22C55E";
    }
});
