"use strict";
(async () => {
  const user = await initPage("student"); if (!user) return;
  const v = $("#view"); v.innerHTML = loadingHtml;
  try {
    const a = await api("/api/analytics"), o = a.overall || {};
    if (!a.timeline.length) { v.innerHTML = `<header class="page-head"><div><h1>Performance analytics</h1></div></header><section class="card">${empty("Complete a quiz to see your analytics.")}</section>`; return; }
    const W = 640, H = 230, L = 36, B = 28, n = a.timeline.length;
    const x = i => L + (n === 1 ? (W - L - 16) / 2 : i * (W - L - 16) / (n - 1)), y = p => H - B - (H - B - 12) * p / 100;
    const pts = a.timeline.map((t, i) => [x(i), y(Number(t.percentage)), t]);
    const grid = [0, 25, 50, 75, 100].map(g => `<line class="gl" x1="${L}" x2="${W - 8}" y1="${y(g)}" y2="${y(g)}"/><text x="4" y="${y(g) + 4}">${g}%</text>`).join("");
    const chart = `<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Quiz scores over time">${grid}<polyline class="ln" points="${pts.map(p => p[0].toFixed(1) + "," + p[1].toFixed(1)).join(" ")}"/>${pts.map(p => `<circle class="pt" cx="${p[0].toFixed(1)}" cy="${p[1].toFixed(1)}" r="4"><title>${esc(p[2].quiz_title)}: ${pctText(p[2].percentage)}</title></circle>`).join("")}<text x="${L}" y="${H - 8}">Oldest attempt</text><text x="${W - 90}" y="${H - 8}">Latest attempt</text></svg>`;
    const bs = a.best_subject, ws = a.weakest_subject, ch = a.last_change;
    v.innerHTML = `<header class="page-head"><div><h1>Performance analytics</h1><p>Based on your submitted attempts.</p></div></header>
    <div class="grid stats">${stat("Overall pass rate", pctText(o.pass_rate))}${stat("Average score", pctText(o.avg_percentage))}${stat("Best subject", bs ? bs.subject_code + " (" + pctText(bs.avg_percentage) + ")" : "—")}${stat("Weakest subject", ws ? ws.subject_code + " (" + pctText(ws.avg_percentage) + ")" : "—")}${stat("Change vs previous attempt", ch == null ? "—" : (ch > 0 ? "+" : "") + num(ch, 1) + " pts")}</div>
    <div class="grid c2"><section class="card"><h2>Subject-wise average score</h2>${a.subjects.map(s => `<div class="prog-row"><div class="row"><b>${esc(s.subject_code)}</b><span style="color:var(--muted)">${pctText(s.avg_percentage)} avg · best ${pctText(s.best_percentage)} · ${s.attempts} attempts</span></div>${bar(s.avg_percentage)}</div>`).join("")}</section>
    <section class="card"><h2>Attempts over time</h2>${chart}</section></div>
    <section class="card"><h2>Improvement between attempts</h2>${a.improvement.length ? `<div class="table-wrap"><table><thead><tr><th>Quiz</th><th>Attempts</th><th>First</th><th>Latest</th><th>Change</th></tr></thead><tbody>${a.improvement.map(i => `<tr><td>${esc(i.quiz_title)}</td><td>${i.attempts}</td><td>${pctText(i.first)}</td><td>${pctText(i.latest)}</td><td class="${i.change >= 0 ? "up" : "down"}">${i.change > 0 ? "+" : ""}${num(i.change, 1)} pts</td></tr>`).join("")}</tbody></table></div>` : empty("Attempt a quiz twice to see your improvement.")}</section>`;
  } catch (e) { showError(v, e); }
})();
