"use strict";
/* subjects.html, subject-detail.html and quiz-instructions.html */
(async () => {
  const user = await initPage("student"); if (!user) return;
  const v = $("#view"), page = document.body.dataset.page; v.innerHTML = loadingHtml;
  try {
    if (page === "subjects") {
      const list = await api("/api/subjects");
      v.innerHTML = `<header class="page-head"><div><h1>Subjects</h1><p>Choose a subject to explore its quizzes.</p></div></header><div class="grid c3">${list.map(s => `<div class="card subj" style="--c:${esc(s.accent_color)};margin:0"><h3><span>${esc(s.code)}</span></h3><p><b style="color:var(--text)">${esc(s.name)}</b><br>${esc(s.description)}</p><p style="margin-bottom:.4rem">${s.quiz_count} quizzes · ${s.question_count} questions</p>${bar(s.quiz_count ? 100 * s.quizzes_attempted / s.quiz_count : 0, s.accent_color)}<p style="margin:.4rem 0 1rem;font-size:.85rem">Progress: ${s.quizzes_attempted}/${s.quiz_count} quizzes · avg ${pctText(s.avg_percentage)}</p><a class="btn" href="subject-detail.html?id=${s.id}">Explore quizzes</a></div>`).join("")}</div>`;
    } else if (page === "subject-detail") {
      const id = needId("id"); if (!id) { location.replace("subjects.html"); return; }
      const s = await api("/api/subjects/" + id);
      v.innerHTML = `<header class="page-head"><div><p style="margin:0"><a href="subjects.html">← All subjects</a></p><h1>${esc(s.code)} — ${esc(s.name)}</h1><p>${esc(s.description)}</p><div>${s.topics.map(t => `<span class="chip">${esc(t)}</span>`).join("")}</div></div></header>
      ${s.quizzes.length ? `<div class="grid c3">${s.quizzes.map(q => {
        const act = q.in_progress_attempt_id ? `<a class="btn" href="quiz.html?attempt=${q.in_progress_attempt_id}">Resume quiz</a>` : q.can_start ? `<a class="btn" href="quiz-instructions.html?id=${q.id}">Start Quiz</a>` : `<button class="btn" disabled>No attempts left</button>`;
        return `<div class="card" style="margin:0"><div class="row">${diffBadge(q.difficulty)}${q.attempted ? badge("Attempted", "ok") : badge("New")}</div><h3 style="margin-top:.5rem">${esc(q.title)}</h3><p style="margin-bottom:.5rem">${esc(q.topic_name || "Mixed topics")}</p><p style="font-size:.9rem">${q.question_count} questions · ${q.duration_minutes} min · ${num(q.max_score, 2)} marks · pass ${num(q.passing_percentage, 0)}%<br>Best score: ${pctText(q.best_percentage)}</p>${act}</div>`; }).join("")}</div>` : empty("No quizzes are available for this subject yet.")}`;
    } else if (page === "instructions") {
      const id = needId("id"); if (!id) { location.replace("subjects.html"); return; }
      const q = await api("/api/quizzes/" + id);
      const neg = Number(q.negative_marks) > 0 ? `Negative marking applies: <b>−${num(q.negative_marks, 2)}</b> for each wrong answer. Unanswered questions score 0.` : "There is <b>no negative marking</b>. Unanswered questions score 0.";
      v.innerHTML = `<header class="page-head"><div><p style="margin:0"><a href="subject-detail.html?id=${q.subject_id}">← ${esc(q.subject_code)}</a></p><h1>${esc(q.title)}</h1><p>${esc(q.description)}</p></div></header>
      <div class="grid stats">${stat("Questions", q.question_count)}${stat("Duration", q.duration_minutes + " min")}${stat("Total marks", num(q.max_score, 2))}${stat("Passing", num(q.passing_percentage, 0) + "%")}</div>
      <section class="card"><h2>Quiz rules</h2><ul>
        <li>The timer starts as soon as you press <b>Start Quiz</b> and keeps running even if you close the page.</li>
        <li>${neg}</li><li>You can move between questions, change answers and <b>mark questions for review</b> before submitting.</li>
        <li>When time runs out your answers are <b>submitted automatically</b>. The server decides the official deadline.</li>
        <li>Press <b>Submit</b> only once — a submitted quiz cannot be changed.</li>
        <li>${q.max_attempts == null ? "You may attempt this quiz as many times as you like." : `You have <b>${q.attempts_left}</b> of ${q.max_attempts} attempts left.`}</li>
        <li>${q.allow_review ? "You can review correct answers and explanations after submitting." : "Answer review is not available for this quiz."}</li></ul>
        <div class="row" style="margin-top:1rem"><button class="btn" id="go" ${q.can_start || q.in_progress_attempt_id ? "" : "disabled"}>${q.in_progress_attempt_id ? "Resume Quiz" : "Start Quiz"}</button><a class="btn ghost" href="subject-detail.html?id=${q.subject_id}">Cancel</a></div><div id="msg" style="margin-top:1rem"></div></section>`;
      $("#go").addEventListener("click", async e => {
        e.target.disabled = true;
        try { const r = await api(`/api/quizzes/${id}/start`, { method: "POST" }); location.href = "quiz.html?attempt=" + r.attempt_id; }
        catch (err) { showError($("#msg"), err); e.target.disabled = false; }
      });
    }
  } catch (e) { showError(v, e); }
})();
