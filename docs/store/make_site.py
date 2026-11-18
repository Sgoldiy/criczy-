#!/usr/bin/env python3
"""Builds the public website that GitHub Pages serves: a landing page and the privacy policy.

  python3 docs/store/make_site.py <out-dir> [--draft]

Reads docs/PRIVACY_POLICY.md (headings, paragraphs, lists, _emphasis_, **bold** and [links](url)) and
docs/store/icon-512.png. Refuses to build while the policy still asks for a support email, because
Google Play requires a contact in the policy; --draft builds anyway, for a local preview.
The .github/workflows/pages.yml workflow runs this on every change on main.
"""
import html
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
POLICY = os.path.join(ROOT, 'docs', 'PRIVACY_POLICY.md')
ICON = os.path.join(ROOT, 'docs', 'store', 'icon-512.png')
PLACEHOLDER = 'add your support email here'

STYLE = """
:root { color-scheme: dark; --ink: #0B1018; --card: #131A24; --text: #E7EAF0; --muted: #A9B3C3; --amber: #FFB627; --line: #283344; }
* { box-sizing: border-box; }
body { margin: 0; background: radial-gradient(900px 500px at 85% -10%, rgba(255,182,39,.12), transparent 60%), var(--ink);
  color: var(--text); font: 17px/1.65 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
main { max-width: 760px; margin: 0 auto; padding: 48px 20px 72px; }
a { color: var(--amber); }
h1 { font-size: 2.1rem; line-height: 1.2; margin: 0 0 6px; letter-spacing: .5px; }
h2 { font-size: 1.2rem; margin: 2.2em 0 .4em; padding-top: 1.2em; border-top: 1px solid var(--line); }
p, li { color: var(--text); }
em { color: var(--muted); font-style: normal; }
.brand { display: flex; align-items: center; gap: 16px; margin-bottom: 28px; text-decoration: none; color: var(--text); }
.brand img { width: 56px; height: 56px; border-radius: 14px; }
.brand b { font-size: 1.6rem; letter-spacing: 2px; }
.brand b span { color: var(--amber); }
.hero { text-align: center; padding-top: 56px; }
.hero img { width: 128px; height: 128px; border-radius: 30px; box-shadow: 0 20px 50px rgba(0,0,0,.5); }
.hero h1 { font-size: 3rem; letter-spacing: 3px; margin-top: 24px; }
.hero h1 span { color: var(--amber); }
.hero p { color: var(--muted); font-size: 1.15rem; max-width: 520px; margin: 8px auto 32px; }
.links { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
.links a { padding: 10px 20px; border-radius: 999px; background: var(--card); border: 1px solid var(--line); text-decoration: none; }
footer { margin-top: 56px; color: var(--muted); font-size: .9rem; text-align: center; }
"""


def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r'\[([^\]]+)\]\(([^)\s]+)\)', lambda m: f'<a href="{html.escape(m.group(2))}">{m.group(1)}</a>', text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', text)
    text = re.sub(r'(?<!\w)_([^_]+)_(?!\w)', r'<em>\1</em>', text)
    return text


def markdown(source):
    """The small subset of Markdown the policy uses."""
    out, paragraph, items = [], [], []

    def flush():
        if paragraph:
            out.append(f'<p>{inline(" ".join(paragraph))}</p>')
            paragraph.clear()
        if items:
            out.append('<ul>' + ''.join(f'<li>{inline(i)}</li>' for i in items) + '</ul>')
            items.clear()

    for line in source.splitlines():
        stripped = line.strip()
        if not stripped:
            flush()
        elif stripped.startswith('## '):
            flush()
            out.append(f'<h2>{inline(stripped[3:])}</h2>')
        elif stripped.startswith('# '):
            flush()
            out.append(f'<h1>{inline(stripped[2:])}</h1>')
        elif stripped.startswith(('- ', '* ')):
            if paragraph:
                flush()
            items.append(stripped[2:])
        else:
            paragraph.append(stripped)
    flush()
    return '\n'.join(out)


def page(title, description, body):
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<link rel="icon" href="icon-512.png">
<style>{STYLE}</style>
</head>
<body><main>
{body}
<footer>Criczy is an independent app. It is not affiliated with or endorsed by any cricket board, league or team.</footer>
</main></body>
</html>
"""


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if len(args) != 1:
        sys.exit(__doc__)
    out = args[0]
    policy = open(POLICY, encoding='utf-8').read()
    if PLACEHOLDER in policy and '--draft' not in sys.argv:
        sys.exit('docs/PRIVACY_POLICY.md still asks for a support email. Add it, then build again (or pass --draft to preview).')
    os.makedirs(out, exist_ok=True)
    shutil.copy(ICON, os.path.join(out, 'icon-512.png'))
    brand = '<a class="brand" href="./"><img src="icon-512.png" alt=""><b>CRIC<span>ZY</span></b></a>'
    with open(os.path.join(out, 'privacy.html'), 'w', encoding='utf-8') as f:
        f.write(page('Criczy privacy policy', 'How the Criczy cricket app handles your data.', brand + markdown(policy)))
    with open(os.path.join(out, 'index.html'), 'w', encoding='utf-8') as f:
        f.write(page('Criczy: live cricket scores', 'Live cricket scores, ball-by-ball commentary, shot replays and fan polls.', """
<section class="hero">
<img src="icon-512.png" alt="Criczy icon">
<h1>CRIC<span>ZY</span></h1>
<p>Live cricket scores, ball-by-ball commentary, shot replays and fan polls, for Android.</p>
<div class="links"><a href="privacy.html">Privacy policy</a></div>
</section>"""))
    print('Wrote', ', '.join(sorted(os.listdir(out))), 'to', out)


if __name__ == '__main__':
    main()
