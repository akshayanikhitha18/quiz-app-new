"use strict";
/* admin-dashboard.html, admin-quizzes.html, admin-questions.html (the server re-checks admin rights on every call) */
(async () => {
  const user = await initPage("admin"); if (!user) return;
  const v = $("#view"), page = document.body.dataset.page; v.innerHTML = loadingHtml;
  const subjectsOf = topics => { const m = {}; topics.forEach(t => { m[t.subject_id] = t.subject_code; }); return m; };
  const dialog = (title, html) => { const d = document.createElement("dialog"); d.className = "dlg"; d.innerHTML = `<h2>${esc(title)}</h2>${html}`; document.body.append(d); d.addEventListener("close", () => d.remove()); d.showModal(); return d; };
  try {
    if (page === "admin-dashboard") {
      const [d, an, students] = await Promise.all([api("/api/dashboard"), api("/api/admin/analytics"), api("/api/admin/students")]);
      const t = d.totals;
      v.innerHTML = `<header class="page-head"><div><h1>Administrator overview</h1><p>Aggregate activity across all students.</p></div></header>
      <div class="grid stats">${stat("Registered students", t.students)}${stat("Quizzes (active)", t.quizzes + " (" + t.active_quizzes + ")")}${stat("Questions", t.questions)}${stat("Submitted attempts", t.attempts)}${stat("Average score", pctText(t.avg_percentage))}</div>
      <div class="grid c2"><section class="card"><h2>Subject-wise performance</h2><div class="table-wrap"><table><thead><tr><th>Subject</th><th>Attempts</th><th>Students</th><th>Avg %</th><th>Pass rate</th></tr></thead><tbody>${an.subjects.map(s => `<tr><td>${esc(s.code)}</td><td>${s.attempts}</td><td>${s.students}</td><td>${pctText(s.avg_percentage)}</td><td>${pctText(s.pass_rate)}</td></tr>`).join("")}</tbody></table></div></section>
      <section class="card"><h2>Score distribution</h2>${an.distribution.length ? an.distribution.map(b => `<div class="prog-row"><div class="row"><b>${esc(b.bucket)}</b><span style="color:var(--muted)">${b.attempts} attempts</span></div>${bar(100 * b.attempts / Math.max(1, t.attempts))}</div>`).join("") : empty("No attempts yet.")}</section></div>
      <section class="card"><h2>Quiz statistics</h2><div class="table-wrap"><table><thead><tr><th>Quiz</th><th>Subject</th><th>Attempts</th><th>Students</th><th>Avg %</th><th>Best %</th><th>Pass rate</th></tr></thead><tbody>${an.quizzes.map(q => `<tr><td>${esc(q.title)}</td><td>${esc(q.subject_code)}</td><td>${q.attempts}</td><td>${q.students}</td><td>${pctText(q.avg_percentage)}</td><td>${pctText(q.best_percentage)}</td><td>${pctText(q.pass_rate)}</td></tr>`).join("")}</tbody></table></div></section>
      <section class="card"><h2>Recent student attempts</h2>${d.recent.length ? `<div class="table-wrap"><table><thead><tr><th>Student</th><th>Quiz</th><th>Score</th><th>Result</th><th>Date</th></tr></thead><tbody>${d.recent.map(r => `<tr><td>${esc(r.full_name)} <span style="color:var(--muted)">@${esc(r.username)}</span></td><td>${esc(r.quiz_title)}</td><td>${num(r.score, 2)} / ${num(r.max_score, 2)} (${pctText(r.percentage)})</td><td>${passBadge(r.passed)}</td><td>${fmtDate(r.submitted_at)}</td></tr>`).join("")}</tbody></table></div>` : empty("No attempts yet.")}</section>
      <section class="card"><h2>Students</h2>${students.length ? `<div class="table-wrap"><table><thead><tr><th>Name</th><th>Username</th><th>Email</th><th>Joined</th><th>Attempts</th><th>Avg %</th></tr></thead><tbody>${students.map(s => `<tr><td>${esc(s.full_name)}</td><td>${esc(s.username)}</td><td>${esc(s.email)}</td><td>${fmtDate(s.created_at)}</td><td>${s.attempts}</td><td>${pctText(s.avg_percentage)}</td></tr>`).join("")}</tbody></table></div>` : empty("No students have registered yet.")}</section>`;
    }

    if (page === "admin-quizzes") {
      const topics = await api("/api/admin/topics"), codes = subjectsOf(topics);
      const subjectIds = Object.keys(codes);
      const load = async () => {
        const quizzes = await api("/api/admin/quizzes");
        $("#qlist").innerHTML = quizzes.length ? `<div class="table-wrap"><table><thead><tr><th>Title</th><th>Subject</th><th>Level</th><th>Qs</th><th>Min</th><th>Pass</th><th>Neg.</th><th>Attempts</th><th>Status</th><th></th></tr></thead><tbody>${quizzes.map(q => `<tr><td>${esc(q.title)}</td><td>${esc(q.subject_code)}</td><td>${diffBadge(q.difficulty)}</td><td>${q.question_count}</td><td>${q.duration_minutes}</td><td>${num(q.passing_percentage, 0)}%</td><td>${num(q.negative_marks, 2)}</td><td>${q.attempt_count}</td><td>${q.is_active ? badge("Active", "ok") : badge("Inactive")}</td>
          <td class="row"><button class="btn ghost sm" data-a="edit" data-id="${q.id}">Edit</button><button class="btn ghost sm" data-a="qs" data-id="${q.id}">Questions</button><button class="btn ghost sm" data-a="tog" data-id="${q.id}">${q.is_active ? "Deactivate" : "Activate"}</button></td></tr>`).join("")}</tbody></table></div>` : empty("No quizzes yet.");
        $$("#qlist button").forEach(b => b.addEventListener("click", () => act(b.dataset.a, Number(b.dataset.id), quizzes)));
      };
      const payload = q => ({ subject_id: q.subject_id, topic_id: q.topic_id, title: q.title, description: q.description, difficulty: q.difficulty, duration_minutes: q.duration_minutes, marks_per_question: q.marks_per_question, negative_marks: q.negative_marks, passing_percentage: q.passing_percentage, max_attempts: q.max_attempts, allow_review: q.allow_review, is_active: q.is_active });
      const act = async (a, id, quizzes) => {
        const q = quizzes.find(x => x.id === id);
        if (a === "tog") { try { await api("/api/admin/quizzes/" + id, { method: "PUT", body: { ...payload(q), is_active: !q.is_active } }); load(); } catch (e) { toast(e.message, "err"); } }
        if (a === "edit") quizForm(q);
        if (a === "qs") pickQuestions(q);
      };
      const quizForm = q => {
        const n = !q, o = q || { difficulty: "easy", duration_minutes: 10, marks_per_question: 1, negative_marks: 0, passing_percentage: 40, allow_review: 1, subject_id: subjectIds[0] };
        const d = dialog(n ? "New quiz" : "Edit quiz", `<form id="qf" novalidate>
          <div class="field"><label for="title">Title</label><input id="title" name="title" value="${esc(o.title || "")}"></div>
          <div class="field"><label for="description">Description</label><textarea id="description" name="description">${esc(o.description || "")}</textarea></div>
          <div class="two"><div class="field"><label for="subject_id">Subject</label><select id="subject_id" name="subject_id">${subjectIds.map(s => `<option value="${s}" ${Number(o.subject_id) === Number(s) ? "selected" : ""}>${esc(codes[s])}</option>`).join("")}</select></div>
          <div class="field"><label for="topic_id">Topic</label><select id="topic_id" name="topic_id"></select></div></div>
          <div class="two"><div class="field"><label for="difficulty">Difficulty</label><select id="difficulty" name="difficulty">${["easy", "medium", "hard"].map(x => `<option ${o.difficulty === x ? "selected" : ""}>${x}</option>`).join("")}</select></div>
          <div class="field"><label for="duration_minutes">Duration (minutes)</label><input id="duration_minutes" name="duration_minutes" type="number" min="1" max="300" value="${o.duration_minutes}"></div></div>
          <div class="two"><div class="field"><label for="marks_per_question">Marks per question</label><input id="marks_per_question" name="marks_per_question" type="number" step="0.25" min="0.25" value="${num(o.marks_per_question, 2)}"></div>
          <div class="field"><label for="negative_marks">Negative marks (0 = off)</label><input id="negative_marks" name="negative_marks" type="number" step="0.25" min="0" value="${num(o.negative_marks, 2)}"></div></div>
          <div class="two"><div class="field"><label for="passing_percentage">Passing percentage</label><input id="passing_percentage" name="passing_percentage" type="number" min="0" max="100" value="${num(o.passing_percentage, 0)}"></div>
          <div class="field"><label for="max_attempts">Max attempts (blank = unlimited)</label><input id="max_attempts" name="max_attempts" type="number" min="1" value="${o.max_attempts == null ? "" : o.max_attempts}"></div></div>
          <div class="field"><label><input type="checkbox" name="allow_review" ${o.allow_review ? "checked" : ""}> Allow answer review after submission</label></div>
          <div id="qmsg"></div><div class="row end"><button type="button" class="btn ghost" id="cancel">Cancel</button><button class="btn" type="submit">Save quiz</button></div></form>`);
        const f = $("#qf", d), fill = () => { const sid = Number(f.subject_id.value); f.topic_id.innerHTML = '<option value="">(mixed topics)</option>' + topics.filter(t => t.subject_id === sid).map(t => `<option value="${t.id}" ${o.topic_id === t.id ? "selected" : ""}>${esc(t.name)}</option>`).join(""); };
        fill(); f.subject_id.addEventListener("change", () => { o.topic_id = null; fill(); });
        $("#cancel", d).onclick = () => d.close();
        f.addEventListener("submit", async e => {
          e.preventDefault();
          const body = { title: f.title.value, description: f.description.value, subject_id: f.subject_id.value, topic_id: f.topic_id.value || null, difficulty: f.difficulty.value, duration_minutes: f.duration_minutes.value, marks_per_question: f.marks_per_question.value, negative_marks: f.negative_marks.value, passing_percentage: f.passing_percentage.value, max_attempts: f.max_attempts.value || null, allow_review: f.allow_review.checked, is_active: n ? false : !!q.is_active };
          try { await api(n ? "/api/admin/quizzes" : "/api/admin/quizzes/" + q.id, { method: n ? "POST" : "PUT", body }); d.close(); toast("Quiz saved", "okk"); load(); }
          catch (err) { showError($("#qmsg", d), err); }
        });
      };
      const pickQuestions = async q => {
        const [all, cur] = await Promise.all([api("/api/admin/questions?subject_id=" + q.subject_id), api("/api/admin/quizzes/" + q.id)]);
        const chosen = new Set(cur.question_ids), usable = all.filter(x => x.is_active || chosen.has(x.id));
        const d = dialog("Questions for " + q.title, `<p>Tick the questions that belong to this quiz (${esc(q.subject_code)} questions only).</p><div class="qpick">${usable.map(x => `<label><input type="checkbox" value="${x.id}" ${chosen.has(x.id) ? "checked" : ""}><span>${diffBadge(x.difficulty)} ${esc(x.question_text.slice(0, 110))}${x.is_active ? "" : " (inactive)"}</span></label>`).join("") || empty("No questions yet for this subject.")}</div><div id="pmsg" style="margin-top:.8rem"></div><div class="row end" style="margin-top:1rem"><button class="btn ghost" id="cancel">Cancel</button><button class="btn" id="save">Save selection</button></div>`);
        $("#cancel", d).onclick = () => d.close();
        $("#save", d).onclick = async () => {
          const ids = $$("input:checked", d).map(c => Number(c.value));
          try { await api(`/api/admin/quizzes/${q.id}/questions`, { method: "PUT", body: { question_ids: ids } }); d.close(); toast("Questions updated", "okk"); load(); }
          catch (err) { showError($("#pmsg", d), err); }
        };
      };
      v.innerHTML = `<header class="page-head"><div><h1>Quizzes</h1><p>Create, edit, activate and deactivate quizzes. Quizzes are never deleted, so student history stays intact.</p></div><button class="btn" id="new">+ New quiz</button></header><section class="card" id="qlist">${loadingHtml}</section>`;
      $("#new").addEventListener("click", () => quizForm(null)); await load();
    }

    if (page === "admin-questions") {
      const topics = await api("/api/admin/topics"), codes = subjectsOf(topics);
      const load = async () => {
        const p = new URLSearchParams(); if ($("#fs").value) p.set("subject_id", $("#fs").value); if ($("#fd").value) p.set("difficulty", $("#fd").value);
        const rows = await api("/api/admin/questions?" + p);
        $("#list").innerHTML = rows.length ? `<div class="table-wrap"><table><thead><tr><th>#</th><th>Question</th><th>Subject / topic</th><th>Level</th><th>Used</th><th>Status</th><th></th></tr></thead><tbody>${rows.map(r => `<tr><td>${r.id}</td><td>${esc(r.question_text.slice(0, 100))}</td><td>${esc(r.subject_code)} / ${esc(r.topic_name)}</td><td>${diffBadge(r.difficulty)}</td><td>${r.times_used} attempts · ${r.quiz_count} quizzes</td><td>${r.is_active ? badge("Active", "ok") : badge("Inactive")}</td><td class="row"><button class="btn ghost sm" data-a="e" data-id="${r.id}">Edit</button><button class="btn danger sm" data-a="d" data-id="${r.id}">Delete</button></td></tr>`).join("")}</tbody></table></div>` : empty("No questions match.");
        $$("#list button").forEach(b => b.addEventListener("click", () => b.dataset.a === "e" ? form(Number(b.dataset.id)) : del(Number(b.dataset.id))));
      };
      const del = async id => {
        if (!await confirmBox("Delete question?", "<p>This cannot be undone. Questions used in attempts or still in a quiz cannot be deleted.</p>", "Delete")) return;
        try { await api("/api/admin/questions/" + id, { method: "DELETE" }); toast("Deleted", "okk"); load(); } catch (e) { toast(e.message, "err"); }
      };
      const form = async id => {
        const q = id ? await api("/api/admin/questions/" + id) : { options: [], difficulty: "easy", is_active: 1, times_used: 0 };
        const opt = i => q.options[i] || { text: "", is_correct: i === 0 };
        const locked = q.times_used > 0;
        const d = dialog(id ? "Edit question" : "New question", `<form id="qf" novalidate>${locked ? '<div class="alert">This question is part of past attempts: text, topic and options are locked. You can change the explanation, difficulty and active flag.</div>' : ""}
          <div class="field"><label for="topic_id">Topic</label><select id="topic_id" name="topic_id" ${locked ? "disabled" : ""}>${topics.map(t => `<option value="${t.id}" ${q.topic_id === t.id ? "selected" : ""}>${esc(t.subject_code)} — ${esc(t.name)}</option>`).join("")}</select></div>
          <div class="field"><label for="difficulty">Difficulty</label><select id="difficulty">${["easy", "medium", "hard"].map(x => `<option ${q.difficulty === x ? "selected" : ""}>${x}</option>`).join("")}</select></div>
          <div class="field"><label for="text">Question</label><textarea id="text" ${locked ? "disabled" : ""}>${esc(q.question_text || "")}</textarea></div>
          <label>Options (select the correct one)</label>${[0, 1, 2, 3].map(i => `<div class="opt-edit"><input type="radio" name="correct" value="${i}" aria-label="Option ${"ABCD"[i]} is correct" ${opt(i).is_correct ? "checked" : ""} ${locked ? "disabled" : ""}><input data-o="${i}" placeholder="Option ${"ABCD"[i]}" value="${esc(opt(i).text)}" ${locked ? "disabled" : ""}></div>`).join("")}
          <div class="field"><label for="exp">Explanation</label><textarea id="exp">${esc(q.explanation || "")}</textarea></div>
          <div class="field"><label><input type="checkbox" id="act" ${q.is_active ? "checked" : ""}> Active</label></div>
          <div id="qmsg"></div><div class="row end"><button type="button" class="btn ghost" id="cancel">Cancel</button><button class="btn" type="submit">Save question</button></div></form>`);
        $("#cancel", d).onclick = () => d.close();
        $("#qf", d).addEventListener("submit", async e => {
          e.preventDefault();
          const f = e.target, body = { topic_id: f.topic_id.value, difficulty: f.difficulty.value, question_text: f.text.value, explanation: f.exp.value, is_active: f.act.checked,
            options: [0, 1, 2, 3].map(i => ({ text: $(`[data-o="${i}"]`, d).value, is_correct: $("input[name=correct]:checked", d) ? Number($("input[name=correct]:checked", d).value) === i : false })) };
          try { await api(id ? "/api/admin/questions/" + id : "/api/admin/questions", { method: id ? "PUT" : "POST", body }); d.close(); toast("Question saved", "okk"); load(); }
          catch (err) { showError($("#qmsg", d), err); }
        });
      };
      v.innerHTML = `<header class="page-head"><div><h1>Question bank</h1><p>Questions are grouped by subject, topic and difficulty.</p></div><button class="btn" id="new">+ New question</button></header>
        <section class="card"><div class="filters"><div><label for="fs">Subject</label><select id="fs"><option value="">All subjects</option>${Object.keys(codes).map(k => `<option value="${k}">${esc(codes[k])}</option>`).join("")}</select></div><div><label for="fd">Difficulty</label><select id="fd"><option value="">All</option><option>easy</option><option>medium</option><option>hard</option></select></div></div><div id="list">${loadingHtml}</div></section>`;
      $("#new").addEventListener("click", () => form(null)); $("#fs").addEventListener("change", load); $("#fd").addEventListener("change", load); await load();
    }
  } catch (e) { showError(v, e); }
})();
