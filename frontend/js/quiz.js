"use strict";
/* The active quiz. The browser only ever receives question text and options - never the answer key. */
(async () => {
  const user = await initPage("student", { shell: false }); if (!user) return;
  const attemptId = needId("attempt"); if (!attemptId) { location.replace("subjects.html"); return; }
  const root = $("#view"); root.innerHTML = loadingHtml;
  const toResults = () => location.replace("results.html?attempt=" + attemptId);
  let data;
  try { data = await api(`/api/attempts/${attemptId}/questions`); }
  catch (e) {
    if (e.code === "already_submitted" || e.code === "expired") { toResults(); return; }
    root.innerHTML = `<div class="alert err" style="margin:2rem">${esc(e.message)} <a href="subjects.html">Back to subjects</a></div>`; return;
  }
  const qsList = data.questions, total = qsList.length;
  const st = qsList.map(q => ({ sel: q.selected_option_id, mark: !!q.marked_for_review, dirty: false }));
  const anchor = performance.now(), initial = data.attempt.seconds_remaining;
  const remaining = () => Math.max(0, initial - (performance.now() - anchor) / 1000);
  let idx = 0, submitting = false;

  root.innerHTML = `<div class="qtop"><div><b>${esc(data.attempt.subject_code)}</b> · ${esc(data.attempt.quiz_title)}<div class="savestat" id="save" aria-live="polite"></div></div>
    <div class="row"><span id="counter"></span><span class="timer" id="timer" role="timer" aria-label="Time remaining"></span><button class="btn" id="submit">Submit quiz</button></div></div>
    <div class="bar" style="border-radius:0"><span id="prog" style="width:0;background:var(--teal)"></span></div>
    <div class="qlayout"><section class="card" id="qcard" aria-live="polite"></section>
    <aside class="card"><h3>Question palette</h3><div class="palette" id="pal"></div>
    <div class="legend"><i style="background:var(--btn)"></i>Answered &nbsp;<i style="background:var(--surface-2);border:1px solid var(--line)"></i>Unanswered<br><i style="background:var(--warn)"></i>Marked for review</div></aside></div>`;
  const setSave = t => { $("#save").textContent = t; };

  function paint() {
    const q = qsList[idx], s = st[idx];
    $("#counter").textContent = `Question ${idx + 1} of ${total}`;
    $("#qcard").innerHTML = `<div class="qtext">${idx + 1}. ${esc(q.text)}</div>
      ${q.options.map(o => `<label class="opt ${s.sel === o.id ? "sel" : ""}"><input type="radio" name="opt" value="${o.id}" ${s.sel === o.id ? "checked" : ""}><span><span class="lt">${esc(o.label)}.</span> ${esc(o.text)}</span></label>`).join("")}
      <div class="row between" style="margin-top:1.2rem"><div class="row"><button class="btn ghost" id="prev" ${idx === 0 ? "disabled" : ""}>← Previous</button><button class="btn" id="next" ${idx === total - 1 ? "disabled" : ""}>Next →</button></div>
      <div class="row"><button class="btn ghost sm" id="clear" ${s.sel == null ? "disabled" : ""}>Clear answer</button><button class="btn ghost sm" id="mark" aria-pressed="${s.mark}">${s.mark ? "★ Marked for review" : "☆ Mark for review"}</button></div></div>`;
    $$("input[name=opt]").forEach(r => r.addEventListener("change", () => { s.sel = Number(r.value); save(idx); paint(); }));
    $("#prev").onclick = () => go(idx - 1); $("#next").onclick = () => go(idx + 1);
    $("#clear").onclick = () => { s.sel = null; save(idx); paint(); };
    $("#mark").onclick = () => { s.mark = !s.mark; save(idx); paint(); };
    $("#pal").innerHTML = qsList.map((x, i) => `<button class="pal ${st[i].sel != null ? "ans" : ""} ${st[i].mark ? "mark" : ""} ${i === idx ? "cur" : ""}" data-i="${i}" aria-label="Question ${i + 1}${st[i].sel != null ? ", answered" : ", unanswered"}${st[i].mark ? ", marked" : ""}">${i + 1}</button>`).join("");
    $$(".pal").forEach(b => b.addEventListener("click", () => go(Number(b.dataset.i))));
    $("#prog").style.width = (100 * st.filter(x => x.sel != null).length / total) + "%";
  }
  const go = i => { if (i >= 0 && i < total) { idx = i; paint(); } };

  async function save(i) {
    const q = qsList[i], s = st[i];
    try {
      await api(`/api/attempts/${attemptId}/answers`, { method: "POST", body: { question_id: q.question_id, selected_option_id: s.sel, marked_for_review: s.mark } });
      s.dirty = false; setSave("All changes saved");
    } catch (e) {
      if (e.code === "already_submitted" || e.code === "closed") { toResults(); return; }
      s.dirty = true; setSave("Not saved yet — will be sent when you submit");
    }
  }

  async function doSubmit(auto) {
    if (submitting) return; submitting = true; $("#submit").disabled = true;
    setSave(auto ? "Time is up — submitting…" : "Submitting…");
    const answers = qsList.map((q, i) => ({ question_id: q.question_id, selected_option_id: st[i].sel, marked_for_review: st[i].mark }));
    try { await api(`/api/attempts/${attemptId}/submit`, { method: "POST", body: { answers } }); toResults(); }
    catch (e) {
      if (e.code === "already_submitted" || e.code === "expired") { toResults(); return; }
      submitting = false; $("#submit").disabled = false; setSave(""); toast(e.message, "err");
    }
  }

  $("#submit").addEventListener("click", async () => {
    if (submitting) return;
    const un = st.filter(x => x.sel == null).length, mk = st.filter(x => x.mark).length;
    const ok = await confirmBox("Submit quiz?", `<p>You answered <b>${total - un}</b> of ${total} questions.${un ? ` <b>${un}</b> unanswered.` : ""}${mk ? ` <b>${mk}</b> marked for review.` : ""}</p><p>You cannot change your answers after submitting.</p>`, "Submit now");
    if (ok) doSubmit(false);
  });

  const tick = () => {
    const r = remaining(), t = $("#timer");
    t.textContent = String(Math.floor(r / 60)).padStart(2, "0") + ":" + String(Math.floor(r % 60)).padStart(2, "0");
    t.classList.toggle("low", r <= 60);
    if (r <= 0 && !submitting) { clearInterval(timerId); doSubmit(true); }
  };
  paint(); tick(); const timerId = setInterval(tick, 500);
})();
