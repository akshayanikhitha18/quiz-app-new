"use strict";
/* results.html and review.html */
(async () => {
  const user = await initPage("student"); if (!user) return;
  const v = $("#view"), page = document.body.dataset.page, id = needId("attempt");
  if (!id) { location.replace("history.html"); return; }
  v.innerHTML = loadingHtml;
  try {
    if (page === "results") {
      const r = await api(`/api/attempts/${id}/result`), pct = Number(r.percentage);
      v.innerHTML = `<header class="page-head"><div><p style="margin:0"><a href="history.html">← History</a></p><h1>${esc(r.quiz_title)}</h1><p>${esc(r.subject_name)} · ${fmtDate(r.submitted_at)}${r.auto_submitted ? " · auto-submitted when time ran out" : ""}</p></div></header>
      <section class="card row" style="gap:2rem"><div class="ring ${r.passed ? "pass" : "fail"}" style="--p:${pct}"><div>${num(pct, 1)}%</div></div>
      <div><h2>${r.passed ? "Passed" : "Not passed"} ${passBadge(r.passed)}</h2><p style="max-width:460px">${esc(r.feedback)}</p><p>Pass mark: ${num(r.passing_percentage, 0)}%</p></div></section>
      <div class="grid stats">${stat("Score", num(r.score, 2) + " / " + num(r.max_score, 2))}${stat("Correct", r.correct)}${stat("Incorrect", r.incorrect)}${stat("Unanswered", r.unanswered)}${stat("Time taken", fmtDur(r.time_taken_seconds))}</div>
      <div class="row">${r.allow_review ? `<a class="btn" href="review.html?attempt=${id}">Review answers</a>` : ""}<a class="btn ghost" href="dashboard.html">Back to dashboard</a><a class="btn ghost" href="quiz-instructions.html?id=${r.quiz_id}">Try again</a></div>`;
    } else {
      const d = await api(`/api/attempts/${id}/review`);
      v.innerHTML = `<header class="page-head"><div><p style="margin:0"><a href="results.html?attempt=${id}">← Results</a></p><h1>Review: ${esc(d.result.quiz_title)}</h1><p>${d.result.correct} correct · ${d.result.incorrect} incorrect · ${d.result.unanswered} unanswered</p></div></header>` +
        d.questions.map(q => `<section class="card q-review ${esc(q.status)}"><div class="row between"><b>Question ${q.position}</b>${badge(q.status, q.status === "correct" ? "ok" : q.status === "incorrect" ? "bad" : "")}</div><p style="color:var(--text);font-weight:600;margin-top:.5rem">${esc(q.text)}</p>
        ${q.options.map(o => `<div class="opt-r ${o.is_correct ? "right" : (o.id === q.selected_option_id ? "wrong" : "")}"><b>${esc(o.label)}.</b> ${esc(o.text)} ${o.is_correct ? "✔ correct answer" : ""}${o.id === q.selected_option_id ? " (your answer)" : ""}</div>`).join("")}
        ${q.status === "unanswered" ? '<p style="margin-top:.5rem">You did not answer this question.</p>' : ""}<p style="margin:.7rem 0 0"><b style="color:var(--text)">Explanation:</b> ${esc(q.explanation)}</p></section>`).join("");
    }
  } catch (e) {
    if (e.code === "in_progress") { location.replace("quiz.html?attempt=" + id); return; }
    showError(v, e);
  }
})();
