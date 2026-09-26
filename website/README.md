# Vertex Website

The public website for Vertex - built with Flask, per `ROADMAP.md`'s "Planned —
the website" entry. Pages: Home/Changelog, Download (the default landing page),
Credits, a "How It's Built" overview, and Features.

This is a first version - the pages, routing, and styling are done and tested
locally, but it has **not been deployed anywhere**. Deploying it (to the Oracle
Cloud instance the roadmap entry mentions, or anywhere else) is a separate step
this doesn't attempt, since that needs access to that actual machine.

## Why Flask reads files from the main repo instead of duplicating content

The Features page and the "How It's Built" page render `FEATURES.md` and
`HOW_VERTEX_WORKS.md` (from the repo root) directly, converting them from
Markdown to HTML at request time - so editing those two files (which you'd do
anyway to keep the app's own docs current) is what keeps this site's content
current too. The Download page reads the `VERSION` file the same way, so the
version number shown here can never drift from what the jars are actually
stamped with.

The Home/Changelog page is the one exception - it uses a short, hand-curated
highlights list (`CHANGELOG_HIGHLIGHTS` in `app.py`), deliberately **not** a
dump of `ROADMAP.md`'s "Done" section. That section is a detailed developer
changelog, written for someone about to read the source; this is a public one,
meant to stay short. Update the list by hand when something is worth telling
players about, not on every commit.

## Running it locally

```
cd website
pip install -r requirements.txt
python app.py
```

Then open `http://localhost:5000`. It finds the repo root (for `VERSION`,
`FEATURES.md`, `HOW_VERTEX_WORKS.md`, and `VertexClient.jar`) automatically,
since this folder lives inside the same repo - override with the
`VERTEX_REPO_ROOT` environment variable if you ever deploy this folder
somewhere separate from the rest of the repo.

**Testing note**: every page was verified locally (`curl` against every route,
plus real screenshots via headless Chromium) at both desktop width and down to
500px. Below 500px, the headless Chromium build available in the environment
this was built in clamps the viewport to a 500px floor regardless of what
width is requested - confirmed directly (`window.innerWidth` stayed 500
however small a width was asked for), not a CSS problem. The CSS itself uses
only standard, well-supported responsive techniques (a `viewport` meta tag,
relative units, plain inline-wrapping nav links, `flex-wrap`, one `max-width:
600px` media query) with nothing that depends on a wide viewport to work
correctly, so it's expected to behave properly on real phone-width screens -
worth a quick real-device check before or shortly after deploying, since that
couldn't be verified directly here.

## Downloads

The Download page links to `VertexClient.jar` only, served directly from the
repo root (already committed there, pre-built - see the root `README.md`). The
server jar is **deliberately not offered here** - hosting isn't something to
casually advertise as a one-click download on the public site, the same
reasoning behind removing the in-app "Host a Server" client feature (see
`ROADMAP.md`, 2026-09-25). Someone who wants to host reads the GitHub repo's
setup instructions instead, a deliberately higher-friction path. The
`/downloads/<filename>` route only ever serves `VertexClient.jar` regardless of
what filename is requested. If a deploy doesn't have that jar sitting next to
the site (e.g. a partial checkout), the link will 404 until it's added back or
`VERTEX_REPO_ROOT` is pointed at a checkout that has it.

## Deploying (not done yet - notes for whenever it happens)

This is a standard Flask app - the usual options apply: a WSGI server (gunicorn
or similar) behind nginx, a systemd service to keep it running, HTTPS via
Let's Encrypt. Nothing about this app is tied to any specific host - it just
needs Python 3, the two `pip install` packages, and read access to the three
repo files and one jar it reads. Not attempted here since it needs the actual
target machine.

## What's deliberately not built yet

- No admin/CMS for the changelog - it's a plain Python list, edited by hand.
- No analytics, no contact form, no anything requiring a database - this is a
  static-content site with four small dynamic touches (the version number and
  three Markdown-rendered pages), and doesn't need one.
