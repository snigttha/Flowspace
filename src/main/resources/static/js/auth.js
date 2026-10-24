/* Login and register pages.
   Talks to the Java backend and shows server errors inside the form (no alert pop-ups). */
(() => {
  const form = document.getElementById("f");
  if (!form) return;

  const page = document.body.dataset.page;
  if (page !== "login" && page !== "register") return;

  const API = "/api";
  const validator = window.FSV || window.FlowValidate;

  // Live field validation, if validate.js supports it.
  if (validator && typeof validator.live === "function") validator.live(form);

  // A form-level message spot, for errors that don't belong to one field.
  let formError = form.querySelector('.err[data-for="form"]');
  if (!formError) {
    formError = document.createElement("small");
    formError.className = "err";
    formError.dataset.for = "form";
    formError.setAttribute("role", "alert");
    const submit = form.querySelector("button");
    form.insertBefore(formError, submit);
  }

  function clearErrors() {
    form.querySelectorAll(".err").forEach((el) => (el.textContent = ""));
  }

  function showError(field, message) {
    const spot = form.querySelector('.err[data-for="' + field + '"]');
    if (spot) {
      spot.textContent = message;
      const input = form.elements.namedItem(field);
      if (input && input.focus) input.focus();
    } else {
      formError.textContent = message;
    }
  }

  // Decide which field a server message belongs to.
  function fieldFor(message) {
    const m = String(message).toLowerCase();
    if (page === "login") return "password";
    if (m.includes("email")) return "email";
    if (m.includes("password")) return "password";
    if (m.includes("name")) return "name";
    return "form";
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearErrors();

    if (validator && typeof validator.check === "function" && !validator.check(form)) {
      return;
    }

    const button = form.querySelector("button");
    const originalText = button ? button.textContent : "";
    if (button) {
      button.disabled = true;
      button.textContent = "Please wait...";
    }

    try {
      const data = Object.fromEntries(new FormData(form).entries());

      if (page === "register") {
        if (data.password !== data.confirm) {
          showError("confirm", "Passwords do not match.");
          return;
        }
        delete data.confirm;
      }

      const response = await fetch(
        API + "/" + (page === "register" ? "register" : "login"),
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(data)
        }
      );

      let result = {};
      try {
        result = await response.json();
      } catch (e) {
        // Response had no JSON body; fall back to a generic message below.
      }

      if (!response.ok) {
        const message = result.error || "Something went wrong. Please try again.";
        showError(fieldFor(message), message);
        return;
      }

      window.location.href = page === "register" ? "index.html" : "dashboard.html";
    } catch (error) {
      formError.textContent =
        "Could not connect to FlowSpace. Please check your connection and try again.";
      console.error(error);
    } finally {
      if (button) {
        button.disabled = false;
        button.textContent = originalText;
      }
    }
  });
})();