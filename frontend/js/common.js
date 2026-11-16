"use strict";
/* Shared helpers: HTML escaping, formatting, layout shell, dialogs, page initialisation. */
const $ = (s, r) => (r || document).querySelector(s);
const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
const esc = v => String(v == null ? "" : v).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const num = (v, d) => v == null ? "—" : String(+Number(v).toFixed(d == null ? 1 : d));
const pctText = v => v == null ? "—" : num(v, 1) + "%";
function fmtDate(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString(undefined, { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}
function fmtDur(sec) { sec = Math.max(0, Math.round(sec || 0)); return Math.floor(sec / 60) + "m " + String(sec % 60).padStart(2, "0") + "s"; }
const badge = (t, c) => `<span class="badge ${esc(c || "")}">${esc(t)}</span>`;
const diffBadge = d => badge(d, "d-" + d);
const passBadge = p => p ? badge("Passed", "ok") : badge("Failed", "bad");
function bar(v, color) {
  const w = Math.max(0, Math.min(100, Number(v) || 0));
  return `<div class="bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${w}"><span style="width:${w}%;background:${esc(color || "var(--accent)")}"></span></div>`;
}
const stat = (label, value) => `<div class="stat"><b>${esc(value)}</b><span>${esc(label)}</span></div>`;
const empty = msg => `<div class="empty">${esc(msg)}</div>`;
const loadingHtml = '<p class="loading">Loading…</p>';
function showError(el, e) { el.innerHTML = `<div class="alert err" role="alert">${esc(e && e.message ? e.message : e)}</div>`; }
function toast(msg, type) {
  const t = document.createElement("div");
  t.className = "toast " + (type || ""); t.setAttribute("role", "status"); t.textContent = msg;
  document.body.append(t); setTimeout(() => t.remove(), 3500);
}
function setFieldErrors(form, fields) {
  $$("[data-err]", form).forEach(el => { el.textContent = (fields && fields[el.dataset.err]) || ""; });
}
/* confirmation dialog; `bodyHtml` must already be escaped by the caller */
function confirmBox(title, bodyHtml, okText) {
  return new Promise(resolve => {
    const d = document.createElement("dialog"); d.className = "dlg";
    d.innerHTML = `<h2>${esc(title)}</h2><div>${bodyHtml}</div><div class="row end" style="margin-top:1rem"><button class="btn ghost" value="no">Cancel</button><button class="btn" value="yes" autofocus>${esc(okText || "Confirm")}</button></div>`;
    d.addEventListener("click", e => { const b = e.target.closest("button"); if (b) d.close(b.value); });
    d.addEventListener("close", () => { resolve(d.returnValue === "yes"); d.remove(); });
    document.body.append(d); d.showModal();
  });
}
const NAV = {
  student: [["dashboard.html", "Dashboard"], ["subjects.html", "Subjects"], ["history.html", "History"], ["analytics.html", "Analytics"], ["profile.html", "Profile"]],
  admin: [["admin-dashboard.html", "Overview"], ["admin-quizzes.html", "Quizzes"], ["admin-questions.html", "Questions"]]
};
function buildShell(user, role) {
  const view = $("#view");
  const here = document.body.dataset.nav || location.pathname.split("/").pop();
  const shell = document.createElement("div"); shell.className = "shell";
  shell.innerHTML = `<aside class="side" id="side"><a class="brand" href="${NAV[role][0][0]}"><span class="logo">Q</span>QuizSphere${role === "admin" ? '<span class="admin-tag">ADMIN</span>' : ""}</a>
    <nav aria-label="Main">${NAV[role].map(n => `<a href="${n[0]}" class="${n[0] === here ? "active" : ""}">${n[1]}</a>`).join("")}</nav>
    <div><div class="who">${esc(user.full_name)}<small>${esc(user.role)}</small></div><button class="btn ghost" id="logout" style="margin-top:.6rem;width:100%">Log out</button></div></aside>
    <div class="main"><button class="btn ghost menu-btn" id="menu" aria-label="Toggle menu">☰ Menu</button></div>`;
  document.body.prepend(shell); $(".main", shell).append(view);
  $("#menu").addEventListener("click", () => $("#side").classList.toggle("open"));
  $("#logout").addEventListener("click", logout);
}
async function logout() { try { await api("/api/logout", { method: "POST" }); } catch (e) { /* ignore */ } location.replace("login.html"); }
/* Checks the session; returns the user or null (after redirecting). */
async function initPage(role, opts) {
  let me;
  try { me = await api("/api/me"); }
  catch (e) {
    if (e.status === 401) location.replace("login.html");
    else document.body.innerHTML = `<div class="alert err" style="margin:2rem">${esc(e.message)}</div>`;
    return null;
  }
  API.csrf = me.csrf_token; API.user = me.user;
  if (me.user.role !== role) { location.replace(me.home_page); return null; }
  if (!opts || opts.shell !== false) buildShell(me.user, role);
  return me.user;
}
const qs = name => new URLSearchParams(location.search).get(name);
const needId = name => { const v = qs(name); return /^\d+$/.test(v || "") ? v : null; };
