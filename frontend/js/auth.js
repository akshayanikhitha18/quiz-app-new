"use strict";
/* Public pages: landing (subject preview), login, register. */
(function () {
  const page = document.body.dataset.page;
  if (page === "landing") {
    api("/api/public/subjects").then(list => {
      $("#subject-preview").innerHTML = list.map(s => `<div class="card subj" style="--c:${esc(s.accent_color)}"><h3>${esc(s.code)}</h3><p style="margin:0"><b style="color:var(--text)">${esc(s.name)}</b><br>${esc(s.description)}</p></div>`).join("");
    }).catch(() => { $("#subject-preview").innerHTML = empty("Subjects will appear here once the server is running."); });
  }
  function wirePw() {
    $$(".pw button").forEach(b => b.addEventListener("click", () => {
      const i = b.previousElementSibling; const show = i.type === "password";
      i.type = show ? "text" : "password"; b.textContent = show ? "Hide" : "Show"; b.setAttribute("aria-pressed", show);
    }));
  }
  if (page === "login") {
    wirePw();
    const f = $("#form");
    f.addEventListener("submit", async e => {
      e.preventDefault(); setFieldErrors(f, {}); $("#msg").innerHTML = "";
      const idv = f.identifier.value.trim();
      if (!idv || !f.password.value) { $("#msg").innerHTML = '<div class="alert err" role="alert">Enter your username or email and your password.</div>'; return; }
      const btn = $("button[type=submit]", f); btn.disabled = true;
      try {
        const d = await api("/api/login", { method: "POST", body: { identifier: idv, password: f.password.value } });
        location.href = d.home_page;
      } catch (err) { showError($("#msg"), err); btn.disabled = false; }
    });
  }
  if (page === "register") {
    wirePw();
    const f = $("#form");
    f.addEventListener("submit", async e => {
      e.preventDefault(); $("#msg").innerHTML = "";
      const v = { full_name: f.full_name.value.trim(), username: f.username.value.trim(), email: f.email.value.trim(), password: f.password.value, confirm_password: f.confirm_password.value };
      const errs = {};
      if (v.full_name.length < 2) errs.full_name = "Full name must be at least 2 characters.";
      if (!/^[A-Za-z0-9_]{3,30}$/.test(v.username)) errs.username = "3-30 letters, digits or underscores.";
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(v.email)) errs.email = "Enter a valid email address.";
      if (v.password.length < 8 || !/[A-Za-z]/.test(v.password) || !/\d/.test(v.password)) errs.password = "At least 8 characters with a letter and a digit.";
      if (v.password !== v.confirm_password) errs.confirm_password = "Passwords do not match.";
      setFieldErrors(f, errs);
      if (Object.keys(errs).length) return;
      const btn = $("button[type=submit]", f); btn.disabled = true;
      try {
        await api("/api/register", { method: "POST", body: v });
        $("#msg").innerHTML = '<div class="alert okk" role="status">Account created! Redirecting to login…</div>';
        setTimeout(() => { location.href = "login.html"; }, 1200);
      } catch (err) {
        setFieldErrors(f, err.fields); showError($("#msg"), err); btn.disabled = false;
      }
    });
  }
})();
