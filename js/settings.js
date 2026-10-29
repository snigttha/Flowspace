
const API = "/api";

async function apiRequest(endpoint, method = "GET", data = null) {
    const options = {
        method,
        credentials: "include",
        headers: { "Content-Type": "application/json" }
    };

    if (data !== null) {
        options.body = JSON.stringify(data);
    }

    const response = await fetch(API + endpoint, options);
    const result = await response.json().catch(() => ({}));
        if (response.status === 401) {
        window.location.replace("index.html");
    }
    if (!response.ok) {
        throw new Error(result.error || "Request failed. Please try again.");
    }

    return result;
}

async function loadAccount() {
    try {
        const user = await apiRequest("/me");

        document.getElementById("name").value = user.name;
        document.getElementById("accountInfo").textContent =
            "Logged in as " + user.email;
    } catch (error) {
        document.getElementById("accountInfo").textContent =
            "Please log in to access your settings.";

        if (error instanceof TypeError) {
            document.getElementById("accountInfo").textContent =
                "Cannot connect to the backend. Check that it is running.";
        }
    }
}

document.getElementById("nameForm").addEventListener("submit", async event => {
    event.preventDefault();

    const message = document.getElementById("nameMessage");
    const name = document.getElementById("name").value.trim();

    if (!name) {
        message.textContent = "Please enter your name.";
        return;
    }

    try {
        const result = await apiRequest("/settings/name", "POST", { name });
        message.textContent = "Name updated successfully!";
        document.getElementById("name").value = result.name;
    } catch (error) {
        message.textContent = error.message;
    }
});

document.getElementById("passwordForm").addEventListener("submit", async event => {
    event.preventDefault();

    const message = document.getElementById("passwordMessage");
    const currentPassword =
        document.getElementById("currentPassword").value;
    const newPassword =
        document.getElementById("newPassword").value;

    if (newPassword.length < 8) {
        message.textContent = "Your new password must contain at least 8 characters.";
        return;
    }

    try {
        await apiRequest("/settings/password", "POST", {
            currentPassword,
            newPassword
        });

        message.textContent = "Password changed successfully!";
        document.getElementById("passwordForm").reset();
    } catch (error) {
        message.textContent = error.message;
    }
});

document.getElementById("logoutButton").addEventListener("click", async () => {
    const message = document.getElementById("logoutMessage");

    try {
        await apiRequest("/logout", "POST");
        window.location.href = "index.html";
    } catch (error) {
        message.textContent = error.message;
    }
});

loadAccount();
