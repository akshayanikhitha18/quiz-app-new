"use strict";
(async () => {
  const user = await initPage("student"); if (!user) return;
  const v = $("#view"); v.innerHTML = loadingHtml;
  try {
    const d = await api("/api/dashboard"), s = d.stats || {};
    const cont = d.in_progress.length ? `<section class="card"><h2>Continue learning</h2>${d.in_progress.map(a => `<div class="row between" style="padding:.4rem 0"><span>${badge(a.subject_code)} ${esc(a.title)}</span><a class="btn sm" href="quiz.html?attempt=${a.attempt_id}">Resume</a></div>`).join("")}</section>` : "";
    const recent = d.recent.length ? `<div class="table-wrap"><table><thead><tr><th>Quiz</th><th>Subject</th><th>Score</th><th>Result</th><th>Date</th><th></th></tr></thead><tbody>${d.recent.map(r => `<tr><td>${esc(r.quiz_title)}</td><td>${esc(r.subject_code)}</td><td>${num(r.score, 2)} / ${num(r.max_score, 2)} (${pctText(r.percentage)})</td><td>${passBadge(r.passed)}</td><td>${fmtDate(r.submitted_at)}</td><td><a href="results.html?attempt=${r.attempt_id}">View</a></td></tr>`).join("")}</tbody></table></div>` : empty("No attempts yet. Pick a subject and take your first quiz!");
    const prog = d.subjects.map(x => `<div class="prog-row"><div class="row"><b>${esc(x.code)}</b><span style="color:var(--muted)">${x.quizzes_attempted}/${x.quiz_count} quizzes · avg ${pctText(x.avg_percentage)}</span></div>${bar(x.quiz_count ? 100 * x.quizzes_attempted / x.quiz_count : 0, x.accent_color)}</div>`).join("");
    const rec = d.recommended.length ? `<div class="grid c3">${d.recommended.map(q => `<div class="card" style="margin:0"><div class="row">${badge(q.subject_code)} ${diffBadge(q.difficulty)}</div><h3 style="margin-top:.5rem">${esc(q.title)}</h3><p>${q.question_count} questions · ${q.duration_minutes} min</p><a class="btn sm" href="quiz-instructions.html?id=${q.id}">View quiz</a></div>`).join("")}</div>` : empty("You have tried every quiz — great work!");
    v.innerHTML = `<header class="page-head"><div><h1>Welcome back, ${esc(user.full_name.split(" ")[0])}</h1><p>Here is how your learning is going.</p></div><a class="btn" href="subjects.html">Browse subjects</a></header>
      <section class="grid stats">${stat("Quizzes attempted", s.attempted || 0)}${stat("Average score", pctText(s.avg_percentage))}${stat("Best score", pctText(s.best_percentage))}${stat("Quizzes passed", s.passed_count || 0)}</section>
      ${cont}<div class="grid c2"><section class="card"><h2>Recent attempts</h2>${recent}</section><section class="card"><h2>Subject progress</h2>${prog}</section></div>
      <section class="card"><h2>Recommended for you</h2>${rec}</section>`;
  } catch (e) { showError(v, e); }
})();
