// Plait launch trailer: a 1920×1080 stage scaled to fit, driven by one clock so it can pause, resume and replay.
(() => {
  const BRAND = '#8b7cff', BRAND2 = '#b9a8ff', WARM = '#ffd98a';
  const stage = document.getElementById('stage');
  const canvas = document.getElementById('canvas');
  const bar = document.getElementById('bar');
  const TOTAL = 24600;
  let clock = 0, last = 0, running = true, fired = 0, cues = [];

  function fit() { canvas.style.transform = `scale(${stage.clientWidth / 1920})`; }
  addEventListener('resize', fit); fit();

  const ease = 'cubic-bezier(.2,.9,.25,1)', spring = 'cubic-bezier(.3,1.5,.5,1)';
  function el(html, css = {}) {
    const d = document.createElement('div');
    d.innerHTML = html; const n = d.firstElementChild;
    Object.assign(n.style, { position: 'absolute' }, css);
    canvas.appendChild(n); return n;
  }
  const a = (n, kf, ms, opt = {}) => n.animate(kf, { duration: ms, easing: ease, fill: 'forwards', ...opt });
  // A word arrives blurred and in the brand colour, then settles sharp and white.
  function word(text, x, y, size, opt = {}) {
    const n = el(`<div class="kt">${text}</div>`, { left: x + 'px', top: y + 'px', fontSize: size + 'px', transform: 'translate(-50%,-50%)', whiteSpace: 'nowrap', fontWeight: opt.weight || 500, letterSpacing: opt.ls || '-0.02em' });
    a(n, [{ opacity: 0, filter: 'blur(18px)', transform: 'translate(-50%,-30%)', color: BRAND }, { opacity: 1, filter: 'blur(0)', transform: 'translate(-50%,-50%)', color: BRAND, offset: .45 }, { opacity: 1, color: opt.keep || '#fff', transform: 'translate(-50%,-50%)' }], opt.ms || 900);
    return n;
  }
  // Several words on one line, each arriving in turn: laid out by the browser, so spacing is always right.
  function line(parts, y, size, step = 300, opt = {}) {
    const row = el(`<div class="kline" style="font-size:${size}px">${parts.map(p => `<span>${p}</span>`).join('')}</div>`,
      { left: '960px', top: y + 'px', transform: 'translate(-50%,-50%)' });
    [...row.children].forEach((sp, k) => a(sp, [{ opacity: 0, filter: 'blur(18px)', transform: 'translateY(30%)', color: BRAND }, { opacity: 1, filter: 'blur(0)', transform: 'none', color: BRAND, offset: .45 }, { opacity: 1, transform: 'none', color: opt.keep || '#fff' }], 900, { delay: k * step }));
    return row;
  }
  const out = (n, ms = 450, dy = -20) => a(n, [{ opacity: 1, filter: 'blur(0)' }, { opacity: 0, filter: 'blur(14px)', transform: `translate(-50%,calc(-50% + ${dy}px))` }], ms);
  const outPlain = (n, ms = 450) => a(n, [{ opacity: 1 }, { opacity: 0, filter: 'blur(10px)' }], ms);
  const tag = (text) => `<div class="tag">${text}</div>`;
  const at = (t, f) => cues.push({ t, f });

  function card(inner, x, y, w, tagText, opt = {}) {
    const n = el(`<div class="tcard ${opt.dark ? 'dark' : ''}" style="width:${w}px">${inner}${tagText ? tag(tagText) : ''}</div>`,
      { left: x + 'px', top: y + 'px', transform: 'translate(-50%,-50%)' });
    a(n, [{ opacity: 0, transform: `translate(-50%,-50%) perspective(900px) rotateY(${opt.flip || 0}deg) scale(.86)`, filter: 'blur(10px)' },
      { opacity: 1, transform: 'translate(-50%,-50%) perspective(900px) rotateY(0) scale(1)', filter: 'blur(0)' }], 800, { easing: spring });
    return n;
  }
  const move = (n, x, y, s = 1, ms = 800) => a(n, [{}, { left: x + 'px', top: y + 'px', transform: `translate(-50%,-50%) scale(${s})` }], ms);

  function build() {
    canvas.innerHTML = '<div class="tglow"></div>';
    cues = []; fired = 0;
    const S = {};

    // A · Your calendars, scattered
    at(0, () => {
      S.collage = el(`<div class="collage">
        <div class="mini" style="left:120px;top:140px"><b>Google</b><i></i><i></i><i style="width:60%"></i></div>
        <div class="mini" style="left:700px;top:60px"><b>Outlook</b><i></i><i style="width:70%"></i><i></i></div>
        <div class="mini" style="left:1290px;top:180px"><b>iCloud</b><i style="width:50%"></i><i></i><i></i></div>
        <div class="mini" style="left:380px;top:600px"><b>Teams invite</b><i></i><i style="width:40%"></i></div>
        <div class="mini" style="left:1020px;top:620px"><b>Zoom link</b><i style="width:80%"></i><i></i></div></div>`, { inset: 0 });
      a(S.collage, [{ opacity: 0, transform: 'scale(1.08)' }, { opacity: 1, transform: 'scale(1)' }], 2200, { easing: 'linear' });
    });
    at(250, () => { S.t1 = el(tag('Your').replace('tag', 'tag big'), { left: '760px', top: '520px' }); a(S.t1, [{ transform: 'scale(.4)', opacity: 0 }, { transform: 'scale(1)', opacity: 1 }], 380, { easing: spring }); });
    at(480, () => { S.t2 = el(tag('calendars').replace('tag', 'tag big'), { left: '930px', top: '520px' }); a(S.t2, [{ transform: 'scale(.4)', opacity: 0 }, { transform: 'scale(1)', opacity: 1 }], 380, { easing: spring }); });
    at(1650, () => {
      const flash = el('<div class="flash"></div>', { inset: 0 });
      a(flash, [{ opacity: 0 }, { opacity: .9, offset: .4 }, { opacity: 0 }], 700, { easing: 'linear' });
      const chev = el('<div class="chev"><span>‹</span><span>›</span></div>', { inset: 0 });
      a(chev, [{ opacity: 0, letterSpacing: '0px' }, { opacity: 1, offset: .3 }, { opacity: 0, letterSpacing: '900px' }], 700);
      [S.t1, S.t2].forEach(t => a(t, [{ opacity: 1 }, { opacity: 0, filter: 'blur(16px)' }], 500));
      outPlain(S.collage, 600);
    });

    // B · Shouldn't be a game of switching apps
    at(2200, () => { S.b1 = line(['Shouldn’t', 'be'], 540, 150, 350); });
    at(2300, () => {
      [[260, 330, 40], [1500, 700, 56], [1380, 330, 24]].forEach(([x, y, s], k) => {
        const p = el('<div class="px"></div>', { left: x + 'px', top: y + 'px', width: s + 'px', height: s + 'px' });
        a(p, [{ opacity: 0 }, { opacity: .9 }, { opacity: 0 }, { opacity: .8 }, { opacity: 0 }], 900, { delay: k * 120, easing: 'steps(5)' });
      });
    });
    at(3100, () => { out(S.b1); });
    at(3200, () => { S.b3 = word('a game of', 640, 540, 120); });
    at(3600, () => {
      S.keys = ['Google', 'Outlook', 'iCloud'].map((k, i) => {
        const n = el(`<div class="key"><small>${['tab', 'switch', 'app'][i]}</small>${k}</div>`, { left: 1060 + i * 290 + 'px', top: '540px', transform: 'translate(-50%,-50%)' });
        a(n, [{ opacity: 0, transform: 'translate(-50%,-30%) scale(.6)', filter: 'blur(10px)' }, { opacity: 1, transform: 'translate(-50%,-50%) scale(1)', filter: 'blur(0)' }], 520, { delay: i * 140, easing: spring });
        return n;
      });
      [['Switch+', 1290, 430], ['Copy link+', 1290, 650]].forEach(([t, x, y], k) => {
        const n = el(`<div class="hint">${t}</div>`, { left: x + 'px', top: y + 'px', transform: 'translate(-50%,-50%)' });
        a(n, [{ opacity: 0 }, { opacity: .8 }, { opacity: 0 }], 1100, { delay: 400 + k * 120 });
      });
    });
    at(4500, () => { out(S.b3); S.keys.forEach(k => out(k)); });

    // C · The work around a meeting, piling up
    at(4700, () => {
      S.c1 = card(`<div class="row"><div class="ico" style="background:#4285f4">31</div><div><b>Design review</b><p>Starts in 5 minutes · where’s the link?</p></div><em>now</em></div>`, 960, 540, 860, 'A meeting starts');
    });
    at(5500, () => {
      move(S.c1, 520, 800, .9);
      S.c2 = card(`<div class="hd">Invitation</div><div class="ln">Join: teams.microsoft.com/l/meetup-join/19%3a…</div><div class="ln">Meeting ID 241 556 780 · Passcode kT4wQ2</div><div class="ln dim">Dial in by phone · Find a local number</div>`, 520, 300, 800, 'then you dig for the link', { flip: -35 });
    });
    at(6600, () => {
      S.c3 = card(`<div class="hd">#notes</div><div class="ed">Decided: ship on Friday<span class="cur">|</span></div><div class="tb">B&nbsp; I&nbsp; U&nbsp; ⋯</div>`, 1400, 300, 760, 'then you take notes', { dark: true, flip: 35 });
    });
    at(7700, () => {
      S.c4 = card(`<div class="hd">Create task</div><div class="ck"><i></i>Send the deck to Sam</div><div class="ck"><i></i>Book a room for Friday</div><div class="btns"><span>Cancel</span><span class="go">Save</span></div>`, 1400, 780, 760, 'then you chase follow-ups', { flip: 35 });
    });
    at(9000, () => { [S.c1, S.c2, S.c3, S.c4].forEach(c => { move(c, 960, 540, .55, 500); a(c, [{ opacity: 1, filter: 'blur(0)' }, { opacity: 0, filter: 'blur(14px)' }], 650, { delay: 250 }); }); });

    // D · Or
    at(9800, () => { S.or = word('Or', 960, 540, 420, { weight: 500, ls: '-0.04em', ms: 1100 }); });
    at(11100, () => out(S.or, 400, -40));

    // E · let Plait weave it
    at(11300, () => {
      S.e = line(['let', '<span class="mark inline"><span class="bar"></span>plait</span>', 'weave it'], 540, 76, 380);
      a(S.e.querySelector('.bar'), [{ width: '0px' }, { width: '34px' }], 500, { delay: 500 });
    });
    at(13000, () => out(S.e));

    // F · Connect the calendars you already use
    at(13300, () => { S.f1 = word('Connect your calendars', 960, 540, 64); });
    at(13600, () => {
      const pts = [[520, 540, 'G', '#4285f4'], [960, 250, 'O', '#0078d4'], [1400, 540, 'iC', '#ff9f0a'], [960, 830, 'T', '#5b5fc7'], [700, 300, 'M', '#34a853'], [1220, 800, 'Z', '#2d8cff']];
      const svg = el(`<svg width="1920" height="1080" style="overflow:visible"><polyline points="${[0, 1, 2, 3, 0].map(i => pts[i][0] + ',' + pts[i][1]).join(' ')}" class="net"/><line x1="${pts[4][0]}" y1="${pts[4][1]}" x2="${pts[0][0]}" y2="${pts[0][1]}" class="net"/><line x1="${pts[5][0]}" y1="${pts[5][1]}" x2="${pts[2][0]}" y2="${pts[2][1]}" class="net"/></svg>`, { left: 0, top: 0 });
      svg.querySelectorAll('.net').forEach((l, k) => a(l, [{ strokeDashoffset: 2600 }, { strokeDashoffset: 0 }], 1800, { delay: 300 + k * 150 }));
      S.net = [svg, ...pts.map(([x, y, t, c], k) => {
        const n = el(`<div class="app" style="background:${c}">${t}</div>`, { left: x + 'px', top: y + 'px', transform: 'translate(-50%,-50%)' });
        a(n, [{ opacity: 0, transform: 'translate(-50%,-50%) scale(.3)' }, { opacity: 1, transform: 'translate(-50%,-50%) scale(1)' }], 500, { delay: k * 160, easing: spring });
        return n;
      })];
    });
    at(14900, () => { out(S.f1, 350); S.f2 = word('you already use…', 960, 540, 64); });
    at(16300, () => { out(S.f2); S.net.forEach(n => outPlain(n, 500)); });

    // G · on-device AI, typed
    at(16600, () => {
      S.g = el('<div class="kt type"></div>', { left: '960px', top: '540px', transform: 'translate(-50%,-50%)', fontSize: '150px', color: BRAND, whiteSpace: 'nowrap', fontWeight: 500, letterSpacing: '-0.03em' });
      'on-device AI'.split('').forEach((ch, k) => setTimeoutClock(() => { S.g.textContent += ch; }, k * 70));
    });
    at(17600, () => a(S.g, [{ color: BRAND }, { color: '#fff' }], 500));
    at(18300, () => { a(S.g, [{ fontSize: '150px', top: '540px' }, { fontSize: '60px', top: '540px' }], 600); });
    at(18900, () => { S.g.textContent = 'on-device AI keeps your day moving'; a(S.g, [{ opacity: .3, filter: 'blur(8px)' }, { opacity: 1, filter: 'blur(0)' }], 500); });
    at(19800, () => out(S.g));

    // H · You focus on the meeting that needs you
    at(20000, () => { S.h1 = line(['You focus', 'on the meeting'], 540, 76, 450); });
    at(21000, () => { a(S.h1.children[0], [{ opacity: 1 }, { opacity: 0, filter: 'blur(14px)', transform: 'translateX(-60px)' }], 400); a(S.h1, [{ transform: 'translate(-50%,-50%)' }, { transform: 'translate(-50%,-50%) translateX(-190px)' }], 600, { delay: 200 }); });
    at(21600, () => { out(S.h1, 400); S.h3 = word('that needs you', 960, 540, 76); });
    at(22500, () => out(S.h3, 400));

    // I · End card
    at(22700, () => {
      S.end = el(`<div class="endcard">
        <svg viewBox="0 0 100 100" width="150" height="150"><defs><linearGradient id="tg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6c7cff"/><stop offset="1" stop-color="#b07cff"/></linearGradient></defs><circle cx="50" cy="50" r="50" fill="url(#tg)"/><path class="s" d="M18 50 C 32 20, 46 80, 60 50 S 76 20, 82 50" stroke="#fff"/><path class="s" d="M18 50 C 32 80, 46 20, 60 50 S 76 80, 82 50" stroke="${WARM}"/><path class="s" d="M18 58 C 34 40, 52 74, 82 42" stroke="#9ed8ff"/></svg>
        <div class="mark"><span class="bar" style="width:46px"></span>plait</div>
        <div class="sub">Your day, woven together · Free on Android</div></div>`, { inset: 0 });
      a(S.end, [{ opacity: 0, filter: 'blur(16px)', transform: 'scale(1.05)' }, { opacity: 1, filter: 'blur(0)', transform: 'scale(1)' }], 900);
      S.end.querySelectorAll('.s').forEach((p, k) => a(p, [{ strokeDashoffset: 240 }, { strokeDashoffset: 0 }], 1200, { delay: 200 + k * 150 }));
    });
  }

  // Timers that follow the trailer's own clock, so pause stops them too.
  let timers = [];
  function setTimeoutClock(f, ms) { timers.push({ t: clock + ms, f }); }

  function frame(now) {
    const dt = last ? now - last : 0; last = now;
    if (running) {
      clock += dt;
      while (fired < cues.length && cues[fired].t <= clock) cues[fired++].f();
      timers = timers.filter(x => (x.t <= clock ? (x.f(), false) : true));
      bar.style.width = Math.min(100, clock / TOTAL * 100) + '%';
      if (clock >= TOTAL) restart();
    }
    requestAnimationFrame(frame);
  }
  function restart() { clock = 0; timers = []; build(); cues.sort((x, y) => x.t - y.t); }
  const playBtn = document.getElementById('play');
  playBtn.onclick = () => {
    running = !running; playBtn.textContent = running ? 'Pause' : 'Play';
    canvas.getAnimations({ subtree: true }).forEach(x => running ? x.play() : x.pause());
  };
  document.getElementById('replay').onclick = () => { running = true; playBtn.textContent = 'Pause'; restart(); };
  restart(); requestAnimationFrame(frame);
})();
