"use strict";
(async () => {
  const user = await initPage("student"); if (!user) return;
  const v = $("#view");
  v.innerHTML = `<header class="page-head"><div><h1>Profile and settings</h1><p>Member since ${fmtDate(user.created_at)}</p></div></header>
  <div class="grid c2"><section class="card"><h2>Your details</h2><form id="pf" novalidate>
    <div class="field"><label>Username</label><input value="${esc(user.username)}" disabled></div>
    <div class="field"><label for="full_name">Full name</label><input id="full_name" name="full_name" value="${esc(user.full_name)}" autocomplete="name"><small class="err" data-err="full_name"></small></div>
    <div class="field"><label for="email">Email</label><input id="email" name="email" type="email" value="${esc(user.email)}" autocomplete="email"><small class="err" data-err="email"></small></div>
    <button class="btn" type="submit">Save changes</button><div id="pmsg" style="margin-top:1rem"></div></form></section>
  <section class="card"><h2>Change password</h2><form id="cf" novalidate>
    <div class="field"><label for="current_password">Current password</label><input id="current_password" name="current_password" type="password" autocomplete="current-password"><small class="err" data-err="current_password"></small></div>
    <div class="field"><label for="new_password">New password</label><input id="new_password" name="new_password" type="password" autocomplete="new-password"><small class="err" data-err="new_password"></small></div>
    <div class="field"><label for="confirm_password">Confirm new password</label><input id="confirm_password" name="confirm_password" type="password" autocomplete="new-password"><small class="err" data-err="confirm_password"></small></div>
    <button class="btn" type="submit">Change password</button><div id="cmsg" style="margin-top:1rem"></div></form></section></div>
  <section class="card"><h2>Session</h2><p>Log out of this device.</p><button class="btn ghost" id="lo">Log out</button></section>`;
  $("#lo").addEventListener("click", logout);
  $("#pf").addEventListener("submit", async e => {
    e.preventDefault(); const f = e.target; setFieldErrors(f, {}); $("#pmsg").innerHTML = "";
    try { const d = await api("/api/profile", { method: "PUT", body: { full_name: f.full_name.value, email: f.email.value } }); $("#pmsg").innerHTML = '<div class="alert okk" role="status">Profile updated.</div>'; $(".who").firstChild.textContent = d.user.full_name; }
    catch (err) { setFieldErrors(f, err.fields); showError($("#pmsg"), err); }
  });
  $("#cf").addEventListener("submit", async e => {
    e.preventDefault(); const f = e.target; setFieldErrors(f, {}); $("#cmsg").innerHTML = "";
    try { await api("/api/change-password", { method: "POST", body: { current_password: f.current_password.value, new_password: f.new_password.value, confirm_password: f.confirm_password.value } }); f.reset(); $("#cmsg").innerHTML = '<div class="alert okk" role="status">Password changed. Other devices were logged out.</div>'; }
    catch (err) { setFieldErrors(f, err.fields); showError($("#cmsg"), err); }
  });
})();
