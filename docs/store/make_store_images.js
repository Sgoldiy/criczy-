// Builds the Play Store screenshots (1080x1920) and feature graphic (1024x500) from the PNGs that
// StoreScreenshotsTest renders. Run from the repository root:
//   ./gradlew :app:testDebugUnitTest -Pstore=true --tests '*StoreScreenshotsTest*'
//   node docs/store/make_store_images.js
// Needs Playwright with Chromium (preinstalled in the cloud sessions). Output: docs/store/play/.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = process.cwd();
const shots = path.join(root, 'app/build/screenshots/store');
const out = path.join(root, 'docs/store/play');
const font = (f) => 'file://' + path.join(root, 'app/src/main/res/font', f);
const icon = fs.readFileSync(path.join(root, 'docs/store/icon.svg'), 'utf8');
fs.mkdirSync(out, { recursive: true });

const base = `
@font-face { font-family: Barlow; src: url(${font('barlow_condensed_bold.ttf')}); font-weight: 700; }
@font-face { font-family: Barlow; src: url(${font('barlow_condensed_semibold.ttf')}); font-weight: 600; }
@font-face { font-family: Manrope; src: url(${font('manrope.ttf')}); }
* { box-sizing: border-box; margin: 0; }
body { background: #0B1018; overflow: hidden; font-family: Manrope, sans-serif; color: #E7EAF0; }
.bg { position: absolute; inset: 0; background:
  radial-gradient(900px 600px at 85% 8%, rgba(255,182,39,.20), transparent 60%),
  radial-gradient(800px 700px at 0% 100%, rgba(31,75,180,.35), transparent 60%),
  linear-gradient(160deg, #16233D 0%, #0B1018 55%, #070B12 100%); }
.amber { color: #FFB627; }
.phone { position: absolute; border-radius: 64px; background: #05080D; padding: 18px;
  box-shadow: 0 40px 90px rgba(0,0,0,.55), 0 0 0 2px #2A3546 inset; }
.phone img { display: block; width: 100%; border-radius: 48px; }
`;

const screens = [
  { file: '1-home.png', name: '01-live-scores', title: 'Every match.<br><span class="amber">Live.</span>', sub: 'Tests, ODIs and T20s, ball by ball' },
  { file: '2-live-test.png', name: '02-match-centre', title: 'Follow the <span class="amber">chase</span>', sub: 'Target, run rate and who is at the crease' },
  { file: '3-shot-replay.png', name: '03-shot-replay', title: 'See <span class="amber">every shot</span>', sub: 'Replay any ball, from the bowler’s hand to the rope' },
  { file: '4-analysis-wagon.png', name: '04-analysis', title: 'Analysis that<br><span class="amber">goes deeper</span>', sub: 'Wagon wheels, phases, pace v spin' },
  { file: '5-scorecard.png', name: '05-scorecard-pinned', title: 'Pin the <span class="amber">live score</span>', sub: 'Your match stays in the notification bar', notification: true },
  { file: '6-community.png', name: '06-fan-community', title: 'Talk cricket<br>with <span class="amber">fans</span>', sub: 'Polls and opinions for your team, every match day' },
];

const statIcon = icon.replace(/<svg /, '<svg width="34" height="34" ');
const notification = `
<div style="position:absolute;left:70px;right:70px;top:560px;background:#1E2633;border-radius:36px;padding:30px 34px;
  box-shadow:0 30px 70px rgba(0,0,0,.6);font-size:27px;z-index:2">
  <div style="display:flex;align-items:center;gap:14px;color:#A9B3C3;font-size:24px">
    <div style="width:38px;height:38px;border-radius:50%;overflow:hidden">${statIcon.replace('width="34" height="34"', 'width="38" height="38" viewBox="18 18 72 72"').replace(/viewBox="0 0 108 108"/, '')}</div>
    Criczy · 2nd Test · India v Australia Tests 2026
  </div>
  <div style="margin-top:16px;font-weight:700;font-size:31px;color:#fff">IND 217 &amp; 143/5 (44.0) · AUS 339 &amp; 295/9d</div>
  <div style="margin-top:8px;color:#C9D2E0">Stumps: India require 275 runs to win with 5 wickets remaining</div>
  <div style="margin-top:18px;color:#FFB627;font-weight:600">Stop</div>
</div>`;

function screenshotHtml(s) {
  const img = 'file://' + path.join(shots, s.file);
  return `<html><head><style>${base}
  .cap { position: absolute; left: 80px; right: 80px; top: 110px; text-align: center; }
  h1 { font-family: Barlow; font-weight: 700; font-size: 112px; line-height: .98; letter-spacing: 1px; text-transform: uppercase; color: #fff; }
  p { margin-top: 26px; font-size: 38px; color: #A9B3C3; line-height: 1.3; }
  </style></head><body style="width:1080px;height:1920px">
  <div class="bg"></div>
  <div class="cap"><h1>${s.title}</h1><p>${s.sub}</p></div>
  <div class="phone" style="left:220px;width:640px;top:${s.title.includes('<br>') ? 560 : 480}px"><img src="${img}"></div>
  ${s.notification ? notification : ''}
  </body></html>`;
}

function featureHtml() {
  const a = 'file://' + path.join(shots, '1-home.png');
  const b = 'file://' + path.join(shots, '3-shot-replay.png');
  return `<html><head><style>${base}
  .phone { border-radius: 34px; padding: 9px; } .phone img { border-radius: 26px; }
  </style></head><body style="width:1024px;height:500px">
  <div class="bg"></div>
  <div style="position:absolute;left:64px;top:92px;width:520px">
    <div style="display:flex;align-items:center;gap:26px">
      <div style="width:132px;height:132px;border-radius:30px;overflow:hidden;box-shadow:0 16px 40px rgba(0,0,0,.5)">${icon.replace(/viewBox="0 0 108 108"/, 'viewBox="9 9 90 90" width="132" height="132"')}</div>
      <div style="font-family:Barlow;font-weight:700;font-size:118px;letter-spacing:2px;line-height:1;color:#fff">CRIC<span class="amber">ZY</span></div>
    </div>
    <div style="margin-top:34px;font-family:Barlow;font-weight:600;font-size:46px;line-height:1.08;text-transform:uppercase;color:#fff">Live scores, shot replays<br>and <span class="amber">fan polls</span></div>
  </div>
  <div class="phone" style="left:640px;top:70px;width:220px;transform:rotate(-6deg)"><img src="${a}"></div>
  <div class="phone" style="left:800px;top:110px;width:220px;transform:rotate(5deg)"><img src="${b}"></div>
  </body></html>`;
}

(async () => {
  const browser = await chromium.launch();
  const render = async (html, w, h, file) => {
    const page = await browser.newPage({ viewport: { width: w, height: h } });
    const tmp = path.join(out, '.render.html');
    fs.writeFileSync(tmp, html);
    await page.goto('file://' + tmp);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(200);
    await page.screenshot({ path: path.join(out, file) });
    await page.close();
    fs.unlinkSync(tmp);
  };
  for (const s of screens) await render(screenshotHtml(s), 1080, 1920, s.name + '.png');
  await render(featureHtml(), 1024, 500, 'feature-graphic.png');
  await browser.close();
  console.log('Wrote', fs.readdirSync(out).join(', '));
})();
