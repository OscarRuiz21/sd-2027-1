/* Tipos propios de PBS-FL: el sobre (Chaum, con la ventana de Abe) y la puerta (una rama del OR).
   El motor los registra desde window.RENDER; firma (it, { h, esc }). */
window.RENDER = {
  envelope(it, { h, esc }) {
    const w = it.w || 190, hh = it.h || 110;
    const ww = Math.round(w * 0.46), wx = Math.round(w * 0.08), wy = hh - 42;
    const win = it.win == null ? '' :
      `<g class="win"><rect x="${wx}" y="${wy}" width="${ww}" height="30" rx="3"/>` +
      `<text x="${wx + ww / 2}" y="${wy + 21}" text-anchor="middle" data-slot="win">${esc(it.win)}</text></g>`;
    return h('div', 'envelope', `<svg width="${w}" height="${hh}" viewBox="0 0 ${w} ${hh}">` +
      `<rect class="body" x="1.5" y="1.5" width="${w - 3}" height="${hh - 3}" rx="4"/>` +
      `<path class="flap" d="M2 2L${w / 2} ${Math.round(hh * 0.55)}L${w - 2} 2"/>${win}` +
      `<g class="seal" transform="translate(${w - 24} ${hh - 22})"><circle r="15"/><path d="M-7 0L-2 6L8 -6"/></g></svg>`);
  },
  // Renglón de tabla: cada fila es un elemento, así puede aparecer, atenuarse o resaltarse sola.
  trow(it, { h, esc }) {
    const cols = it.cols || [];
    const d = h('div', 'trow' + (it.head ? ' thead' : '') + (it.hl ? ' hl' : '') + (it.alt ? ' alt' : ''),
      it.cells.map((c, i) => `<div class="c" style="width:${cols[i] || 160}px">${esc(c)}</div>`).join(''));
    d.style.height = (it.h || 44) + 'px';
    return d;
  },
  // Recuadro para señalar una columna o una zona.
  box(it, { h }) {
    const d = h('div', 'box', '');
    d.style.height = (it.h || 100) + 'px';
    return d;
  },
  door(it, { h, esc }) {
    const d = h('div', 'door', `<div class="dl" data-slot="title">${esc(it.t)}</div><span class="knob"></span>` +
      `<div class="ds" data-slot="meta">${esc(it.m || '')}</div><div class="dn" data-slot="note">${esc(it.note || '')}</div>`);
    d.style.height = (it.h || 210) + 'px';
    return d;
  }
};
