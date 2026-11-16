"use strict";
/* All server calls go through api(): JSON in/out, CSRF header, uniform errors. */
const API = { csrf: null, user: null };

class ApiError extends Error {
  constructor(status, code, message, fields) { super(message); this.status = status; this.code = code; this.fields = fields || {}; }
}

async function api(path, opts) {
  const o = opts || {};
  const init = { method: o.method || "GET", headers: {}, credentials: "same-origin" };
  if (o.body !== undefined) { init.headers["Content-Type"] = "application/json"; init.body = JSON.stringify(o.body); }
  if (init.method !== "GET" && API.csrf) init.headers["X-CSRF-Token"] = API.csrf;
  let res;
  try { res = await fetch(path, init); }
  catch (e) { throw new ApiError(0, "network", "Cannot reach the server. Check that it is running and try again."); }
  let payload = null;
  try { payload = await res.json(); } catch (e) { /* not JSON */ }
  if (!res.ok || !payload || payload.ok === false) {
    const err = (payload && payload.error) || {};
    throw new ApiError(res.status, err.code || "error", err.message || "Something went wrong.", err.fields);
  }
  return payload.data;
}
