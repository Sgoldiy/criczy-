// Builds the Play Store images from the PNGs that StoreScreenshotsTest renders:
//   docs/store/play/01..06-*.png   six phone screenshots, 1080 x 1920, one continuous background across the set
//   docs/store/play/tablet-7in, tablet-10in   four landscape tablet screenshots each, 1920 x 1080 and 2560 x 1440
//   docs/store/play/feature-graphic.png   1024 x 500
//   docs/store/icon-512.png        the listing icon, from docs/store/icon.svg (written by make_icon.py)
// Run from the repository root:
//   python3 docs/store/make_icon.py
//   ./gradlew :app:testDebugUnitTest -Pstore=true --tests '*StoreScreenshotsTest*'
//   node docs/store/make_store_images.js
// Needs Playwright with Chromium (preinstalled in the cloud sessions).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = process.cwd();
const shots = path.join(root, 'app/build/screenshots/store');
const out = path.join(root, 'docs/store/play');
const iconSvg = fs.readFileSync(path.join(root, 'docs/store/icon.svg'), 'utf8');
const font = (f) => 'file://' + path.join(root, 'app/src/main/res/font', f);
const url = (f) => 'file://' + path.join(shots, f);
fs.mkdirSync(out, { recursive: true });

const W = 1080;
const H = 1920;
// Crops below are measured on a 1078 px wide render (411dp at 420dpi); the renders can be any density.
const UNIT = 1078;

const css = `
@font-face { font-family: Barlow; src: url(${font('barlow_condensed_bold.ttf')}); font-weight: 700; }
@font-face { font-family: Barlow; src: url(${font('barlow_condensed_semibold.ttf')}); font-weight: 600; }
@font-face { font-family: Manrope; src: url(${font('manrope.ttf')}); }
* { box-sizing: border-box; margin: 0; padding: 0; }
body { position: relative; overflow: hidden; background: #0B1018; font-family: Manrope, sans-serif; color: #E7EAF0; }
.layer { position: absolute; top: 0; }
.amber { background: linear-gradient(180deg, #FFD166, #FF9F1C); -webkit-background-clip: text; background-clip: text; color: transparent; }
.phone { position: absolute; background: linear-gradient(150deg, #6A7586 0%, #1D2430 18%, #0D1117 50%, #1D2430 82%, #59647A 100%);
  box-shadow: 0 70px 120px -30px rgba(0,0,0,.85), 0 30px 60px rgba(0,0,0,.45); }
.phone .btn { position: absolute; border-radius: 3px; background: linear-gradient(90deg, #232A35, #6A7586); }
.bezel { background: #020305; }
.screen { position: relative; overflow: hidden; background: #0B1018; }
.screen img { display: block; }
.status { position: relative; display: flex; align-items: center; justify-content: space-between; color: #fff; font-weight: 600; }
.cam { position: absolute; left: 50%; border-radius: 50%; background: #000; box-shadow: 0 0 0 2px #151a22; }
.callout { position: absolute; z-index: 3; padding: 2px;
  background: linear-gradient(140deg, rgba(255,209,102,.95), rgba(255,182,39,.18) 38%, rgba(255,255,255,.10) 70%, rgba(255,182,39,.45));
  box-shadow: 0 60px 110px -20px rgba(0,0,0,.85), 0 24px 48px rgba(0,0,0,.4), 0 0 70px rgba(255,159,28,.16); }
.callout > div { background-repeat: no-repeat; }
.kicker { display: inline-flex; align-items: center; gap: 14px; font-family: Barlow; font-weight: 600; font-size: 34px; letter-spacing: 5px;
  text-transform: uppercase; color: #FFC24D; padding: 10px 28px 10px 26px; border-radius: 999px;
  background: rgba(255,182,39,.10); border: 1.5px solid rgba(255,182,39,.38); }
.dot { width: 14px; height: 14px; border-radius: 50%; background: #FF4D4F; box-shadow: 0 0 0 6px rgba(255,77,79,.22), 0 0 18px #FF4D4F; }
h1 { font-family: Barlow; font-weight: 700; font-size: 126px; line-height: .93; letter-spacing: .5px; text-transform: uppercase; color: #fff; }
h1 .amber { filter: drop-shadow(0 8px 30px rgba(255,159,28,.28)); }
.sub { font-size: 35px; font-weight: 500; color: #AEB8C8; line-height: 1.3; }
`;

// ------------------------------------------------------------------ background shared by the set

function wave(x0, x1, base, amp, period, phase) {
  let d = '';
  for (let x = x0; x <= x1; x += 6) {
    const y = base + amp * Math.sin((2 * Math.PI * x) / period + phase);
    d += (d ? 'L' : 'M') + x.toFixed(0) + ',' + y.toFixed(1);
  }
  return d;
}

function backdrop(width, height, opts) {
  const { glows, ribbon, dots, rings = [], ball } = opts;
  const glow = glows.map(([x, y, r, c, a]) =>
    `<circle cx="${x}" cy="${y}" r="${r}" fill="url(#${c})" opacity="${a}"/>`).join('');
  const ringEls = rings.map(([x, y, r, dash]) =>
    `<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="#fff" stroke-opacity="${dash ? .07 : .05}" stroke-width="3"${dash ? ' stroke-dasharray="20 26"' : ''}/>`).join('');
  let ballEl = '';
  if (ball) {
    const [x, y, r, angle] = ball; // angle: direction the ball came from, in degrees
    ballEl = `<g transform="translate(${x} ${y}) rotate(${angle})">
      <path d="M0,${-r} L${r * 7},0 L0,${r} Z" fill="url(#trail)"/>
      <path d="M${r * 1.5},${-r - 9} L${r * 3.6},${-r - 9}" stroke="url(#speed)" stroke-width="3" stroke-linecap="round"/>
      <path d="M${r * 2.3},${r + 9} L${r * 3.8},${r + 9}" stroke="url(#speed)" stroke-width="3" stroke-linecap="round"/>
    </g>
    <circle cx="${x}" cy="${y}" r="${r}" fill="url(#ballFill)"/>
    <g transform="translate(${x} ${y}) rotate(28) scale(${r / 7.6})">
      <path d="M-1.6,-7.4C1.6,-3 1.6,3 -1.6,7.4" fill="none" stroke="#fff" stroke-opacity=".95" stroke-width="1.25" stroke-linecap="round"/>
      <path d="M1.1,-7.3C4.3,-3 4.3,3 1.1,7.3" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="1.25" stroke-linecap="round"/>
    </g>`;
  }
  return `<svg class="layer" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="base" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#14203A"/><stop offset=".55" stop-color="#0B1018"/><stop offset="1" stop-color="#070A10"/></linearGradient>
    <radialGradient id="amberGlow"><stop offset="0" stop-color="#FFB627"/><stop offset="1" stop-color="#FFB627" stop-opacity="0"/></radialGradient>
    <radialGradient id="blueGlow"><stop offset="0" stop-color="#2557D6"/><stop offset="1" stop-color="#2557D6" stop-opacity="0"/></radialGradient>
    <linearGradient id="rib" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="${width}" y2="0">
      ${Array.from({ length: 13 }, (_, i) => `<stop offset="${i / 12}" stop-color="${i % 2 ? '#FF9F1C' : '#FFD166'}"/>`).join('')}
    </linearGradient>
    <linearGradient id="trail" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FF7A45" stop-opacity=".75"/><stop offset=".5" stop-color="#FF9F1C" stop-opacity=".25"/><stop offset="1" stop-color="#FF9F1C" stop-opacity="0"/></linearGradient>
    <linearGradient id="speed" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity=".5"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    <radialGradient id="ballFill" cx=".36" cy=".32" r=".8"><stop offset="0" stop-color="#FF6B6F"/><stop offset=".55" stop-color="#E5383B"/><stop offset="1" stop-color="#A80F1A"/></radialGradient>
    <filter id="soft" x="-5%" y="-50%" width="110%" height="200%"><feGaussianBlur stdDeviation="16"/></filter>
    <filter id="grain"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" stitchTiles="stitch"/>
      <feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .5 -.2"/></filter>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#base)"/>
  ${glow}
  ${ringEls}
  <path d="${ribbon}" fill="none" stroke="#FF9F1C" stroke-opacity=".35" stroke-width="34" filter="url(#soft)"/>
  <path d="${ribbon}" fill="none" stroke="url(#rib)" stroke-opacity=".75" stroke-width="5" stroke-linecap="round"/>
  <path d="${dots}" fill="none" stroke="#fff" stroke-opacity=".28" stroke-width="5" stroke-linecap="round" stroke-dasharray="0.1 22"/>
  ${ballEl}
  <rect width="${width}" height="${height}" filter="url(#grain)" opacity=".18"/>
</svg>`;
}

const SLIDES = 6;
const panorama = backdrop(W * SLIDES, H, {
  glows: Array.from({ length: SLIDES }, (_, i) => [
    [i * W + (i % 2 ? 880 : 200), 230, 640, 'amberGlow', .22],
    [i * W + (i % 2 ? 160 : 920), 1560, 780, 'blueGlow', .42],
  ]).flat(),
  ribbon: wave(-20, W * SLIDES + 20, 1230, 300, 2160, 0),
  dots: wave(-20, W * SLIDES + 20, 1290, 230, 2160, 0.95),
  rings: [[3 * W, 1480, 1250, false], [3 * W, 1480, 620, true]],
  ball: [968, 1230 + 300 * Math.sin((2 * Math.PI * 968) / 2160), 30, 140],
});

// ------------------------------------------------------------------ phone and callouts

function statusIcons(k) {
  const s = 19 * k;
  return `<svg width="${s * 3.6}" height="${s}" viewBox="0 0 72 20" fill="#fff">
    <rect x="0" y="13" width="3.5" height="6" rx="1"/><rect x="5" y="9" width="3.5" height="10" rx="1"/><rect x="10" y="5" width="3.5" height="14" rx="1"/><rect x="15" y="1" width="3.5" height="18" rx="1"/>
    <path d="M33 19 L24.5 9.5 A12 12 0 0 1 41.5 9.5 Z"/>
    <rect x="48" y="4" width="20" height="12" rx="3" fill="none" stroke="#fff" stroke-width="1.8"/><rect x="50.5" y="6.5" width="13" height="7" rx="1.5"/><rect x="69" y="8" width="2" height="4" rx="1"/>
  </svg>`;
}

/** A phone showing one render; returns its HTML and where the screen sits, for lining up callouts. */
function phone(file, left, top, width, style = '') {
  const k = width / 600;
  const edge = 5 * k;
  const bezel = 13 * k;
  const screenW = width - 2 * (edge + bezel);
  const statusH = 38 * k;
  const html = `<div class="phone" style="left:${left}px;top:${top}px;width:${width}px;padding:${edge}px;border-radius:${80 * k}px;${style}">
    <i class="btn" style="top:${200 * k}px;height:${120 * k}px;width:${5 * k}px;right:${-3 * k}px"></i>
    <i class="btn" style="top:${350 * k}px;height:${80 * k}px;width:${5 * k}px;right:${-3 * k}px"></i>
    <div class="bezel" style="padding:${bezel}px;border-radius:${75 * k}px">
      <div class="screen" style="border-radius:${62 * k}px">
        <div class="status" style="height:${statusH}px;padding:0 ${34 * k}px 0 ${38 * k}px;font-size:${20 * k}px">
          <span>8:24</span><b class="cam" style="width:${20 * k}px;height:${20 * k}px;top:${9 * k}px;margin-left:${-10 * k}px"></b>${statusIcons(k)}
        </div>
        <img src="${url(file)}" style="width:${screenW}px">
      </div>
    </div>
  </div>`;
  return { html, screenTop: top + edge + bezel + statusH, screenLeft: left + edge + bezel, scale: screenW / UNIT };
}

/** A piece of a render, enlarged over the same piece in the phone (which it fully covers), nudged by dx/dy. */
function callout(c, ph) {
  const s = c.scale;
  let w, h, sx, sy, radius, cx, cy;
  if (c.rect) {
    const [x1, y1, x2, y2] = c.rect;
    [w, h, sx, sy, radius, cx, cy] = [(x2 - x1) * s, (y2 - y1) * s, x1, y1, c.radius * s, (x1 + x2) / 2, (y1 + y2) / 2];
  } else {
    const [ccx, ccy, r] = c.circle;
    [w, h, sx, sy, radius, cx, cy] = [2 * r * s, 2 * r * s, ccx - r, ccy - r, r * s, ccx, ccy];
  }
  const left = ph.screenLeft + cx * ph.scale + (c.dx || 0) - w / 2 - 2;
  const top = ph.screenTop + cy * ph.scale + (c.dy || 0) - h / 2 - 2;
  return `<div class="callout" style="left:${left}px;top:${top}px;border-radius:${radius + 2}px">
    <div style="width:${w}px;height:${h}px;border-radius:${radius}px;background-image:url(${url(c.file)});background-size:${UNIT * s}px auto;background-position:${-sx * s}px ${-sy * s}px"></div>
  </div>`;
}

function notification(top) {
  const stat = iconSvg.replace(/<svg ([^>]*)viewBox="[^"]*"/, '<svg $1viewBox="18 18 72 72" width="46" height="46"');
  return `<div style="position:absolute;z-index:3;left:56px;right:56px;top:${top}px;border-radius:44px;padding:30px 36px 30px;
      background:linear-gradient(180deg,#252E3C,#1C232E);border:1.5px solid rgba(255,255,255,.07);
      box-shadow:0 60px 110px -20px rgba(0,0,0,.85),0 24px 48px rgba(0,0,0,.4),0 0 70px rgba(255,159,28,.14)">
    <div style="display:flex;align-items:center;gap:16px;font-size:24px;color:#AEB8C8">
      <div style="width:46px;height:46px;border-radius:50%;overflow:hidden;flex:none">${stat}</div>
      <span><b style="color:#E7EAF0;font-weight:600">Criczy</b> · 2nd Test · India v Australia Tests 2026 · now</span>
      <svg style="margin-left:auto" width="28" height="28" viewBox="0 0 24 24"><path d="M6 9l6 6 6-6" fill="none" stroke="#AEB8C8" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
    </div>
    <div style="margin-top:20px;font-weight:700;font-size:33px;color:#fff;letter-spacing:.2px">IND 217 &amp; 143/5 (44.0) · AUS 339 &amp; 295/9d</div>
    <div style="margin-top:10px;font-size:28px;color:#C9D2E0;line-height:1.35">Stumps: India require 275 runs to win with 5 wickets remaining</div>
    <div style="margin-top:22px;display:inline-block;padding:10px 26px;border-radius:999px;background:rgba(255,182,39,.14);color:#FFC24D;font-weight:700;font-size:25px">Stop</div>
  </div>`;
}

// ------------------------------------------------------------------ the six screenshots

const slides = [
  { name: '01-live-scores', file: '1-home.png', kicker: 'Live scores', live: true, lines: ['Every match.', 'Live.'], sub: 'Tests, ODIs and T20s, ball by ball',
    callout: { file: '1-home.png', rect: [42, 594, 1035, 960], radius: 38, scale: .7, dx: -36, dy: 26 } },
  { name: '02-match-centre', file: '2-live-test.png', kicker: 'Match centre', lines: ['Follow the', 'chase'], sub: 'Target, run rate and who is at the crease',
    callout: { file: '2-live-test.png', rect: [42, 764, 1035, 948], radius: 38, scale: .7, dx: 36 } },
  { name: '03-shot-replay', file: '3-shot-replay.png', kicker: 'Shot replay', lines: ['See', 'every shot'], sub: 'Replay any ball, from the bowler’s hand to the rope',
    callout: { file: '3-shot-replay.png', circle: [538, 1746, 462], scale: .66, dy: 40 } },
  { name: '04-analysis', file: '4-analysis-wagon.png', kicker: 'Analysis', lines: ['Analysis that', 'goes deeper'], sub: 'Wagon wheels, phases and pace v spin',
    callout: { file: '4-analysis-wagon.png', circle: [300, 1908, 228], scale: 1.3, dx: -40, dy: -20 } },
  { name: '05-pinned-score', file: '5-scorecard.png', kicker: 'Pinned score', lines: ['Pin the', 'live score'], sub: 'Your match stays in the notification bar',
    notification: true },
  { name: '06-fan-community', file: '6-community.png', kicker: 'Fan community', lines: ['Talk cricket', 'with fans'], sub: 'Polls and opinions for your team, every match day',
    callout: { file: '6-community.png', rect: [42, 824, 1035, 1486], radius: 38, scale: .66, dx: -36, dy: -14 } },
];

const PHONE_W = 600;
const PHONE_TOP = 560;

function slideHtml(s, i) {
  const ph = phone(s.file, (W - PHONE_W) / 2, PHONE_TOP, PHONE_W);
  const extra = s.notification ? notification(PHONE_TOP + 150) : s.callout ? callout(s.callout, ph) : '';
  return `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style></head>
  <body style="width:${W}px;height:${H}px">
    <div class="layer" style="left:${-i * W}px">${panorama}</div>
    <div style="position:absolute;left:60px;right:60px;top:104px;text-align:center">
      <div class="kicker">${s.live ? '<span class="dot"></span>' : ''}${s.kicker}</div>
      <h1 style="margin-top:34px">${s.lines[0]}<br><span class="amber">${s.lines[1]}</span></h1>
      <div class="sub" style="margin-top:26px">${s.sub}</div>
    </div>
    ${ph.html}
    ${extra}
  </body></html>`;
}

// ------------------------------------------------------------------ tablets (landscape, 16:9)

/** A landscape tablet showing one render, with a status bar and the camera in the bezel. */
function tablet(file, left, top, width) {
  const k = width / 1180;
  const edge = 5 * k;
  const bezel = 26 * k;
  const screenW = width - 2 * (edge + bezel);
  const statusH = 30 * k;
  return `<div class="phone" style="left:${left}px;top:${top}px;width:${width}px;padding:${edge}px;border-radius:${52 * k}px">
    <div class="bezel" style="position:relative;padding:${bezel}px;border-radius:${47 * k}px">
      <b class="cam" style="width:${10 * k}px;height:${10 * k}px;top:${8 * k}px;margin-left:${-5 * k}px"></b>
      <div class="screen" style="border-radius:${16 * k}px">
        <div class="status" style="height:${statusH}px;padding:0 ${26 * k}px;font-size:${17 * k}px">
          <span>8:24</span>${statusIcons(k * 0.95)}
        </div>
        <img src="${url(file)}" style="width:${screenW}px">
      </div>
    </div>
  </div>`;
}

const tabletSlides = [
  { name: '01-live-scores', shot: '1-home', kicker: 'Live scores', live: true, lines: ['Every match.', 'Live.'], sub: 'Tests, ODIs and T20s, ball by ball' },
  { name: '02-match-centre', shot: '2-match', kicker: 'Match centre', lines: ['Follow the', 'chase'], sub: 'Target, run rate and who is at the crease' },
  { name: '03-scorecard', shot: '3-scorecard', kicker: 'Scorecard', lines: ['Every run,', 'every wicket'], sub: 'Full scorecards, innings by innings' },
  { name: '04-series', shot: '4-series', kicker: 'Series', lines: ['Every series,', 'one tap away'], sub: 'Tours and tournaments for men and women' },
];

function tabletHtml(t, prefix, TW, TH) {
  const k = TW / 1920;
  const bg = backdrop(TW, TH, {
    glows: [[300 * k, 200 * k, 700 * k, 'amberGlow', .22], [1500 * k, 1000 * k, 900 * k, 'blueGlow', .45], [1700 * k, 120 * k, 500 * k, 'amberGlow', .1]],
    ribbon: wave(-10, TW + 10, 900 * k, 90 * k, 2600 * k, 0.6),
    dots: wave(-10, TW + 10, 960 * k, 70 * k, 2600 * k, 1.3),
  });
  const width = 1130 * k;
  const left = TW - width - 64 * k;
  const top = (TH - width * 0.674) / 2;
  return `<!doctype html><html><head><meta charset="utf-8"><style>${css}
  .kicker { font-size: ${30 * k}px; letter-spacing: ${4.5 * k}px; padding: ${9 * k}px ${24 * k}px; gap: ${12 * k}px; }
  .dot { width: ${12 * k}px; height: ${12 * k}px; }
  h1 { font-size: ${98 * k}px; }
  .sub { font-size: ${30 * k}px; }
  </style></head><body style="width:${TW}px;height:${TH}px">
    ${bg}
    <div style="position:absolute;left:${86 * k}px;width:${600 * k}px;top:0;bottom:0;display:flex;flex-direction:column;justify-content:center">
      <div><div class="kicker">${t.live ? '<span class="dot"></span>' : ''}${t.kicker}</div></div>
      <h1 style="margin-top:${30 * k}px">${t.lines[0]}<br><span class="amber">${t.lines[1]}</span></h1>
      <div class="sub" style="margin-top:${24 * k}px">${t.sub}</div>
    </div>
    ${tablet(`${prefix}-${t.shot}.png`, left, top, width)}
  </body></html>`;
}

// ------------------------------------------------------------------ feature graphic

function featureHtml() {
  const FW = 1024;
  const FH = 500;
  const bg = backdrop(FW, FH, {
    glows: [[190, 210, 380, 'amberGlow', .32], [930, 470, 460, 'blueGlow', .55], [700, 30, 300, 'amberGlow', .12]],
    ribbon: wave(-10, FW + 10, 462, 46, 1300, 2.2),
    dots: wave(-10, FW + 10, 486, 30, 1300, 2.9),
  });
  const ph = phone('1-home.png', 700, 46, 270, 'transform:rotate(-7deg);transform-origin:50% 0');
  const disc = `<div class="callout" style="left:560px;top:238px;border-radius:50%;transform:rotate(-7deg)">
    <div style="width:236px;height:236px;border-radius:50%;background-image:url(${url('3-shot-replay.png')});background-size:${UNIT * .2554}px auto;background-position:${-(538 - 462) * .2554}px ${-(1746 - 462) * .2554}px;background-repeat:no-repeat"></div></div>`;
  const icon = iconSvg.replace(/<svg ([^>]*)viewBox="[^"]*"/, '<svg $1viewBox="12 12 84 84" width="150" height="150"');
  return `<!doctype html><html><head><meta charset="utf-8"><style>${css}
  .phone { box-shadow: 0 40px 70px -20px rgba(0,0,0,.85), 0 16px 30px rgba(0,0,0,.4); }
  .callout { box-shadow: 0 30px 60px -10px rgba(0,0,0,.8), 0 0 40px rgba(255,159,28,.18); }
  </style></head><body style="width:${FW}px;height:${FH}px">
    ${bg}
    <div style="position:absolute;left:58px;top:74px;display:flex;align-items:center;gap:28px">
      <div style="width:150px;height:150px;border-radius:34px;overflow:hidden;box-shadow:0 22px 50px rgba(0,0,0,.55),0 0 0 1px rgba(255,255,255,.06)">${icon}</div>
      <div style="font-family:Barlow;font-weight:700;font-size:124px;letter-spacing:3px;line-height:1;color:#fff">CRIC<span class="amber">ZY</span></div>
    </div>
    <div style="position:absolute;left:62px;top:262px;font-family:Barlow;font-weight:600;font-size:46px;line-height:1.06;text-transform:uppercase;color:#fff;letter-spacing:.5px">
      Live scores, shot replays<br>and <span class="amber">fan polls</span></div>
    <div style="position:absolute;left:62px;top:384px;display:flex;gap:12px;font-size:21px;font-weight:600;color:#C9D2E0">
      ${['Tests', 'ODIs', 'T20s'].map((t) => `<span style="padding:7px 18px;border-radius:999px;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12)">${t}</span>`).join('')}
    </div>
    ${ph.html}
    ${disc}
  </body></html>`;
}

function iconHtml() {
  const icon = iconSvg.replace(/<svg ([^>]*)viewBox="[^"]*"/, '<svg $1viewBox="12 12 84 84" width="512" height="512"');
  return `<!doctype html><html><body style="margin:0;width:512px;height:512px">${icon}</body></html>`;
}

(async () => {
  for (const s of slides) {
    for (const f of [s.file, s.callout && s.callout.file].filter(Boolean)) {
      if (!fs.existsSync(path.join(shots, f))) throw new Error(`Missing ${f}: run StoreScreenshotsTest with -Pstore=true first`);
    }
  }
  const browser = await chromium.launch();
  const render = async (html, w, h, file) => {
    const page = await browser.newPage({ viewport: { width: w, height: h } });
    const tmp = path.join(out, '.render.html');
    fs.writeFileSync(tmp, html);
    await page.goto('file://' + tmp);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(250);
    await page.screenshot({ path: file });
    await page.close();
    fs.unlinkSync(tmp);
  };
  for (const [i, s] of slides.entries()) await render(slideHtml(s, i), W, H, path.join(out, s.name + '.png'));
  for (const [prefix, folder, TW, TH] of [['tablet7', 'tablet-7in', 1920, 1080], ['tablet10', 'tablet-10in', 2560, 1440]]) {
    fs.mkdirSync(path.join(out, folder), { recursive: true });
    for (const t of tabletSlides) await render(tabletHtml(t, prefix, TW, TH), TW, TH, path.join(out, folder, t.name + '.png'));
  }
  await render(featureHtml(), 1024, 500, path.join(out, 'feature-graphic.png'));
  await render(iconHtml(), 512, 512, path.join(root, 'docs/store/icon-512.png'));
  await browser.close();
  console.log('Wrote', slides.map((s) => s.name).join(', '), ', 7 and 10 inch tablet sets, feature-graphic and icon-512');
})();
