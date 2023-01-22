function showPopup() {
    let popup = document.getElementById("login-failed-popup");
    popup.classList.add("visible-popup");
    setTimeout(() => popup.classList.remove("visible-popup"), 5000)
}