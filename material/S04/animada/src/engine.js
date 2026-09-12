/* Motor de escenas: construye el diagrama, aplica pasos acumulativos y anima subtítulos. */
(() => {
  const D = window.DECK, S = D.scenes;
  const $ = id => document.getElementById(id);
  const stage = $('stage'), layer = $('layer'), wires = $('wires'), head = $('head'),
        cap = $('cap'), notes = $('notes'), hint = $('hint');
  const NS = 'http://www.w3.org/2000/svg';
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const DEF = { chip: [0, 44], metric: [290, 170], term: [440, 160], label: [300, 30], line: [0, 0] };
  const TONE = { chip: 'blue', label: 'green', line: 'red' };
  let si = -1, st = 0, timer = null, reg = {}, geo = {};

  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const h = (tag, cls, html) => { const e = document.createElement(tag); e.className = cls; if (html != null) e.innerHTML = html; return e; };
  const num = n => Math.round(n).toLocaleString('en-US');
  const rich = s => esc(s).replace(/\*([^*]+)\*/g, '<em>$1</em>');

  $('brand').textContent = D.brand || '';
  function fit() { stage.style.setProperty('--s', Math.min(innerWidth / 1600, innerHeight / 900)); }
  addEventListener('resize', fit); fit();

  /* ---------- renderizadores por tipo ---------- */
  const R = {
    node: it => h('div', 'node' + (it.fill ? ' fill' : ''), `<div class="t" data-slot="title">${esc(it.t)}</div><div class="m" data-slot="meta">${esc(it.m)}</div><span class="bar"></span><span class="dot"></span>`),
    chip: it => h('div', 'chip' + (it.mono ? ' mono' : ''), (it.k ? `<span class="k">${esc(it.k)}</span>` : '') + `<span data-slot="text">${esc(it.text)}</span>`),
    label: it => h('div', 'label' + (it.mono ? ' mono' : ''), `<span data-slot="text">${esc(it.text)}</span>`),
    term: it => h('div', 'term', `<div class="k">${esc(it.k)}</div><div class="v" data-slot="text">${rich(it.v)}</div>`),
    line: it => {
      const e = h('div', 'line' + (it.v ? ' v' : ''), it.label ? `<span>${esc(it.label)}</span>` : '');
      if (it.v) e.style.height = it.h + 'px'; else e.style.width = it.w + 'px';
      return e;
    },
    users: it => {
      let icons = '';
      for (let i = 0; i < it.n; i++) {
        const c = i < (it.g || 0) ? 'var(--green)' : 'var(--blue)';
        icons += `<svg viewBox="0 0 42 48"><circle cx="21" cy="11" r="8" style="stroke:${c}"/><path d="M5 46v-8a16 16 0 0 1 32 0v8" style="stroke:${c}"/></svg>`;
      }
      return h('div', 'users', `<div class="row">${icons}</div>` + (it.label != null ? `<div class="cnt" data-slot="count">${esc(it.label)}</div>` : ''));
    },
    img: it => h('div', 'img', `<img src="${esc((window.IMG || {})[it.src] || it.src)}" alt="${esc(it.alt)}" style="height:${it.h || 60}px">`),
    list: it => h('div', 'list' + (it.mono ? ' mono' : ''), (it.head ? `<div class="lh">${esc(it.head)}</div>` : '') +
      `<ul>${(it.rows || []).map((r, i) => `<li data-slot="r${i}">${Array.isArray(r) ? `<b>${esc(r[0])}</b> ${rich(r[1])}` : rich(r)}</li>`).join('')}</ul>`),
    metric: it => h('div', 'metric', `<div class="h">${esc(it.head)}</div>` +
      it.rows.map(([k, v, s]) => `<div class="r"><span class="k">${esc(k)}</span><span class="v" data-slot="${s}">${esc(v)}</span></div>`).join('') +
      `<svg viewBox="0 0 228 36" preserveAspectRatio="none"><path class="calm" d="M0 28L30 26L60 27L90 24L120 25L150 23L180 24L228 22"/><path class="spike" d="M0 30L30 28L60 26L90 22L120 18L150 11L180 6L228 2"/></svg>`)
  };
  Object.assign(R, window.RENDER || {});   // tipos propios del proyecto (src/custom*.js)
  const API = { h, esc, rich };
  const rail = D.rail ? stage.appendChild(h('div', 'rail', '')) : null;

  /* ---------- rutas de conexiones ---------- */
  function anchor(g, s) {
    const cx = g.x + g.w / 2, cy = g.y + g.h / 2;
    return s === 't' ? [cx, g.y] : s === 'b' ? [cx, g.y + g.h] : s === 'l' ? [g.x, cy] : [g.x + g.w, cy];
  }
  function rounded(p, r) {
    let d = `M${p[0][0]} ${p[0][1]}`;
    for (let i = 1; i < p.length - 1; i++) {
      const [x0, y0] = p[i - 1], [x1, y1] = p[i], [x2, y2] = p[i + 1];
      const l1 = Math.hypot(x1 - x0, y1 - y0), l2 = Math.hypot(x2 - x1, y2 - y1);
      if (l1 < 1 || l2 < 1) { d += ` L${x1} ${y1}`; continue; }
      const k = Math.min(r, l1 / 2, l2 / 2);
      d += ` L${x1 - (x1 - x0) / l1 * k} ${y1 - (y1 - y0) / l1 * k} Q${x1} ${y1} ${x1 + (x2 - x1) / l2 * k} ${y1 + (y2 - y1) / l2 * k}`;
    }
    const q = p[p.length - 1];
    return d + ` L${q[0]} ${q[1]}`;
  }
  function route(e) {
    const pt = v => Array.isArray(v) ? { x: v[0], y: v[1], w: 0, h: 0 } : geo[v];
    const a = pt(e.from), b = pt(e.to);
    const dx = (b.x + b.w / 2) - (a.x + a.w / 2), dy = (b.y + b.h / 2) - (a.y + a.h / 2);
    const vert = e.dir ? e.dir === 'v' : Math.abs(dy) > Math.abs(dx) * 0.6;
    const p1 = anchor(a, e.fs || (vert ? (dy > 0 ? 'b' : 't') : (dx > 0 ? 'r' : 'l')));
    const p2 = anchor(b, e.ts || (vert ? (dy > 0 ? 't' : 'b') : (dx > 0 ? 'l' : 'r')));
    let pts;
    if (e.pts || e.straight) pts = [p1, ...(e.pts || []), p2];
    else if (vert) pts = Math.abs(p1[0] - p2[0]) < 2 ? [p1, [p1[0], p2[1]]] : [p1, [p1[0], (p1[1] + p2[1]) / 2], [p2[0], (p1[1] + p2[1]) / 2], p2];
    else pts = Math.abs(p1[1] - p2[1]) < 2 ? [p1, [p2[0], p1[1]]] : [p1, [(p1[0] + p2[0]) / 2, p1[1]], [(p1[0] + p2[0]) / 2, p2[1]], p2];
    return rounded(pts, 14);
  }
  function mkEdge(e) {
    const g = document.createElementNS(NS, 'g'), d = route(e);
    g.innerHTML = `<path class="base" pathLength="1" d="${d}"/><path class="bead" d="${d}"/>` +
      `<g class="x"><circle r="11"/><path d="M-4.5 -4.5L4.5 4.5M4.5 -4.5L-4.5 4.5"/></g>` +
      (e.arrow ? '<path class="head" d="M0 0L-13 -6.5L-13 6.5Z"/>' : '') +
      (e.label ? `<text class="elab" text-anchor="middle">${esc(e.label)}</text>` : '');
    wires.appendChild(g);
    const p = g.querySelector('.bead'), L = p.getTotalLength(), m = p.getPointAtLength(L / 2);
    g.querySelector('.x').setAttribute('transform', `translate(${m.x} ${m.y})`);
    if (e.arrow) {
      const q = p.getPointAtLength(L), r = p.getPointAtLength(Math.max(0, L - 4));
      g.querySelector('.head').setAttribute('transform', `translate(${q.x} ${q.y}) rotate(${Math.atan2(q.y - r.y, q.x - r.x) * 180 / Math.PI})`);
    }
    if (e.label) {
      const [dx, dy] = e.lo || [0, -16], t = g.querySelector('.elab');
      t.setAttribute('x', m.x + dx); t.setAttribute('y', m.y + dy);
    }
    return g;
  }

  /* ---------- construir escena ---------- */
  function build() {
    const sc = S[si];
    reg = {}; geo = {};
    layer.textContent = ''; wires.textContent = '';
    document.body.className = sc.theme ? 'th-' + sc.theme : '';
    if (rail) rail.innerHTML = D.rail.map(b => `<i class="${b === sc.block ? 'on' : ''}"></i>`).join('') + `<b>${esc(sc.block || '')}</b>`;
    head.innerHTML = `<div class="tag">${esc(sc.tag)}</div><h1 class="title">${esc(sc.title)}</h1><p class="sub">${esc(sc.sub)}</p>`;
    for (const it of sc.items || []) {
      const type = it.type || 'node';
      if (!R[type]) throw new Error(`Tipo desconocido "${type}" en el elemento ${it.id}`);
      const el = R[type](it, API), tone = it.tone || TONE[type];
      el.dataset.base = 'it ' + el.className + (tone ? ' t-' + tone : '');
      el.className = el.dataset.base;   // nace oculto (.it sin .on); apply() lo hace entrar
      el.style.left = it.x + 'px'; el.style.top = it.y + 'px';
      if (it.w && type !== 'line') el.style.width = it.w + 'px';
      if (it.h && type === 'node') el.style.height = it.h + 'px';
      el.querySelectorAll('[data-slot]').forEach(s => { s.dataset.key = it.id + '.' + s.dataset.slot; s.dataset.orig = s.textContent; });
      layer.appendChild(el);
      reg[it.id] = { el, kind: 'it' };
      const w = type === 'node' ? (it.w || 220) : type === 'users' ? it.n * 52 - 10 : (it.w || (DEF[type] || [0, 0])[0]);
      const hh = type === 'node' ? (it.h || 104) : type === 'users' ? (it.label != null ? 73 : 48) : (it.h || (DEF[type] || [0, 0])[1]);
      geo[it.id] = { x: it.x, y: it.y, w, h: hh };
    }
    for (const e of sc.edges || []) {
      const g = mkEdge(e);
      g.dataset.base = 'edge t-' + (e.tone || 'blue');
      g.setAttribute('class', g.dataset.base);
      reg[e.id] = { el: g, kind: 'edge', e };
    }
  }

  /* ---------- aplicar pasos 0..st ---------- */
  function apply(animate) {
    const sc = S[si], steps = sc.steps;
    const vis = new Set(), hid = new Set(), state = {}, text = {};
    for (let i = 0; i <= st; i++) {
      const s = steps[i];
      (s.show || []).forEach(id => { vis.add(id); hid.delete(id); });
      (s.hide || []).forEach(id => { vis.delete(id); hid.add(id); });
      for (const [k, v] of Object.entries(s.set || {})) k.split(',').forEach(id => { state[id.trim()] = v; });
      Object.assign(text, s.text || {});
      for (const [k, [a, b, suf = '']] of Object.entries(s.count || {}))
        text[k] = i === st && animate ? { a, b, suf } : num(b) + suf;
    }
    for (const [id, r] of Object.entries(reg)) {
      const on = r.kind === 'edge'
        ? vis.has(id) || (!hid.has(id) && vis.has(r.e.from) && vis.has(r.e.to))
        : vis.has(id);
      const cls = r.el.dataset.base + (on ? ' on' : '') + (state[id] ? ' ' + state[id] : '');
      r.kind === 'edge' ? r.el.setAttribute('class', cls) : (r.el.className = cls);
    }
    layer.querySelectorAll('[data-key]').forEach(t => {
      const v = text[t.dataset.key];
      if (v && typeof v === 'object') return tween(t, v);
      const nv = v ?? t.dataset.orig;
      if (t.textContent !== nv) {
        t.textContent = nv;
        if (animate) { t.classList.remove('flash'); void t.offsetWidth; t.classList.add('flash'); }
      }
    });
    caption(steps[st].say || '', animate);
    const total = S.reduce((n, x) => n + x.steps.length, 0);
    const done = S.slice(0, si).reduce((n, x) => n + x.steps.length, 0) + st + 1;
    $('prog').style.width = (done / total * 100) + '%';
    $('count').textContent = `${String(si).padStart(2, '0')} / ${String(S.length - 1).padStart(2, '0')} · ${st + 1}/${steps.length}`;
    renderNotes();
    try { history.replaceState(null, '', `#${si + 1}.${st + 1}`); } catch (_) {}
  }
  function tween(t, { a, b, suf }) {
    cancelAnimationFrame(t._raf);
    const t0 = performance.now(), dur = reduce ? 1 : 1500;
    const f = now => {
      const k = Math.min(1, (now - t0) / dur);
      t.textContent = num(a + (b - a) * (1 - Math.pow(1 - k, 3))) + suf;
      if (k < 1) t._raf = requestAnimationFrame(f);
    };
    t._raf = requestAnimationFrame(f);
  }
  function caption(say, animate) {
    cap.textContent = '';
    let hl = false;
    say.split(/\s+/).filter(Boolean).forEach((w, i) => {
      let html = '', buf = '';
      const flush = () => { if (buf) html += hl ? `<span class="hl">${esc(buf)}</span>` : esc(buf); buf = ''; };
      for (const ch of w) { if (ch === '*') { flush(); hl = !hl; } else buf += ch; }
      flush();
      const s = h('span', 'w', html);
      s.style.animationDelay = (animate && !reduce ? i * 55 : 0) + 'ms';
      if (!animate) s.style.animationDuration = '.01s';
      cap.append(s, ' ');
    });
  }
  function renderNotes() {
    const sc = S[si];
    notes.innerHTML = `<div class="nt">${esc(sc.tag)} · guion y notas</div><div class="cols">
      <div><h4>Guion</h4><ol>${sc.steps.map((s, j) => `<li class="${j === st ? 'cur' : ''}">${esc((s.say || '').replace(/\*/g, ''))}</li>`).join('')}</ol></div>
      <div><h4>Para explicar</h4>${(sc.notes || []).map(n => `<p>${n}</p>`).join('')}</div></div>`;
  }

  /* ---------- navegación ---------- */
  function go(nsi, nst, animate = true) {
    if (nsi !== si) {
      si = nsi; st = nst; build();
      requestAnimationFrame(() => requestAnimationFrame(() => apply(animate)));
    } else { st = nst; apply(animate); }
  }
  const last = i => S[i].steps.length - 1;
  function next() { if (st < last(si)) go(si, st + 1); else if (si < S.length - 1) go(si + 1, 0); else stop(); }
  function prev() { if (st > 0) go(si, st - 1); else if (si > 0) go(si - 1, last(si - 1)); }
  function stop() { clearTimeout(timer); timer = null; }
  function play() {
    const words = (S[si].steps[st].say || '').split(/\s+/).length;
    timer = setTimeout(() => { if (si === S.length - 1 && st === last(si)) return stop(); next(); play(); }, 1300 + words * 330);
  }
  function fullscreen() {
    try { document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen(); } catch (_) {}
  }

  addEventListener('keydown', e => {
    if (e.metaKey || e.ctrlKey || e.altKey) return;
    const k = e.key;
    if (['ArrowRight', 'ArrowDown', ' ', 'PageDown', 'Enter'].includes(k)) { e.preventDefault(); stop(); next(); }
    else if (['ArrowLeft', 'ArrowUp', 'PageUp', 'Backspace'].includes(k)) { e.preventDefault(); stop(); prev(); }
    else if (k === 'n' || k === 'N') notes.classList.toggle('open');
    else if (k === 'p' || k === 'P') timer ? stop() : play();
    else if (k === 'f' || k === 'F') fullscreen();
    else if (k === 'Home') { stop(); go(0, 0); }
    else if (k === 'End') { stop(); go(S.length - 1, last(S.length - 1)); }
  });
  stage.addEventListener('click', e => {
    stop();
    const r = stage.getBoundingClientRect();
    (e.clientX - r.left < r.width * 0.28) ? prev() : next();
  });
  setTimeout(() => hint.classList.add('gone'), 6000);

  // ?still = estado final del paso sin animación (para capturas sin interfaz)
  const still = /[?&]still\b/.test(location.search);
  if (still) hint.remove();
  const m = /^#(\d+)\.(\d+)$/.exec(location.hash);
  const a = m ? Math.min(Math.max(+m[1] - 1, 0), S.length - 1) : 0;
  const b = m ? Math.min(Math.max(+m[2] - 1, 0), last(a)) : 0;
  go(a, b, !still);
})();
