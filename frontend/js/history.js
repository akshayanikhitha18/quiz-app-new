"use strict";
(async () => {
  const user = await initPage("student"); if (!user) return;
  const v = $("#view"); v.innerHTML = loadingHtml;
  try {
    const subjects = await api("/api/subjects");
    v.innerHTML = `<header class="page-head"><div><h1>Quiz history</h1><p>Every attempt you have made.</p></div></header>
    <section class="card"><div class="filters"><div><label for="f-sub">Subject</label><select id="f-sub"><option value="">All subjects</option>${subjects.map(s => `<option value="${s.id}">${esc(s.code)}</option>`).join("")}</select></div>
    <div><label for="f-st">Result</label><select id="f-st"><option value="">All results</option><option value="passed">Passed</option><option value="failed">Failed</option></select></div>
    <div><label for="f-sort">Sort by</label><select id="f-sort"><option value="date">Date</option><option value="percentage">Percentage</option><option value="score">Score</option><option value="title">Quiz name</option></select></div>
    <div><label for="f-dir">Order</label><select id="f-dir"><option value="desc">Newest / highest first</option><option value="asc">Oldest / lowest first</option></select></div></div><div id="list">${loadingHtml}</div></section>`;
    const load = async () => {
      const p = new URLSearchParams({ sort: $("#f-sort").value, dir: $("#f-dir").value });
      if ($("#f-sub").value) p.set("subject_id", $("#f-sub").value);
      if ($("#f-st").value) p.set("status", $("#f-st").value);
      try {
        const rows = await api("/api/attempts?" + p);
        $("#list").innerHTML = rows.length ? `<div class="table-wrap"><table><thead><tr><th>Quiz</th><th>Subject</th><th>Date</th><th>Score</th><th>%</th><th>Result</th><th>Time</th><th></th></tr></thead><tbody>${rows.map(r => `<tr><td>${esc(r.quiz_title)}</td><td>${esc(r.subject_code)}</td><td>${fmtDate(r.submitted_at)}</td><td>${num(r.score, 2)} / ${num(r.max_score, 2)}</td><td>${pctText(r.percentage)}</td><td>${passBadge(r.passed)}</td><td>${fmtDur(r.time_taken_seconds)}</td><td><a class="btn sm" href="results.html?attempt=${r.attempt_id}">View Results</a></td></tr>`).join("")}</tbody></table></div>` : empty("No attempts match these filters.");
      } catch (e) { showError($("#list"), e); }
    };
    $$("select", v).forEach(s => s.addEventListener("change", load)); load();
  } catch (e) { showError(v, e); }
})();
