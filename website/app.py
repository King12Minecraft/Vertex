"""
Vertex public website - Flask app.

Pages: Home, Download (default landing page), Changelog, Roadmap, Credits, a
simplified public "how it's built" overview, and Features. Reads the
same VERSION file the jars are stamped with, so the version number
never drifts between the app and the site (see the root README's
"Building fresh jars from source" section - build.sh/build.bat stamp
jars from this exact file).

The Features and "how it's built" pages render FEATURES.md and
HOW_VERTEX_WORKS.md directly (converted from Markdown at request time)
rather than duplicating their content here - editing those two files
in the repo root is what keeps this site's content current, the same
way editing them already keeps the app's own docs current.

Run locally: pip install -r requirements.txt && python app.py
Deploy: see README.md in this folder.
"""

import os

import markdown
from flask import Flask, render_template, send_from_directory, abort

app = Flask(__name__)

# The repo root - one level up from this file - is where VERSION,
# FEATURES.md, HOW_VERTEX_WORKS.md, and the pre-built VertexClient.jar all live.
# Overridable via an env var so this can be deployed with the site
# checked out separately from the app's own source, pointed at a copy
# of the repo (or just the handful of files this app actually reads).
REPO_ROOT = os.environ.get("VERTEX_REPO_ROOT", os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def read_repo_file(filename):
    path = os.path.join(REPO_ROOT, filename)
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def get_version():
    version = read_repo_file("VERSION")
    return version.strip() if version else "unknown"


def render_markdown_file(filename):
    text = read_repo_file(filename)
    if text is None:
        return None
    return markdown.markdown(text, extensions=["fenced_code", "tables"])


def parse_changelog(text):
    """Entries of CHANGELOG.md (repo root), newest first: [{"heading": str, "points": [str]}].

    The same hand-written file the app's own Changelog page reads (see the format note at the top
    of the file itself), so the website and the app can never disagree. A "## " line starts an
    entry, a "- " line is a bullet, and a line indented by two spaces continues the bullet above.
    Anything else - the "# " title and the header comment - is ignored.
    """
    entries = []
    current = None
    in_comment = False
    for line in (text or "").splitlines():
        if in_comment:
            if "-->" in line:
                in_comment = False
            continue
        if line.strip().startswith("<!--"):
            if "-->" not in line:
                in_comment = True
            continue
        if line.startswith("## "):
            # "points", not "items" - a dict already has a real .items() method, so Jinja2's
            # dot-access would silently resolve entry.items to that method instead of this key.
            current = {"heading": line[3:].strip(), "points": []}
            entries.append(current)
        elif current is not None and line.startswith("- "):
            current["points"].append(line[2:].strip())
        elif current is not None and current["points"] and line.startswith("  ") and line.strip():
            current["points"][-1] += " " + line.strip()
    return entries


def get_changelog():
    return parse_changelog(read_repo_file("CHANGELOG.md"))


@app.route("/")
def download():
    version = get_version()
    return render_template("download.html", version=version)


@app.route("/home")
def home():
    version = get_version()
    entries = get_changelog()
    return render_template("home.html", version=version, latest=entries[0] if entries else None)


@app.route("/changelog")
def changelog():
    return render_template("changelog.html", entries=get_changelog())


@app.route("/roadmap")
def roadmap():
    # Hand-written and public: possibilities, no dates, and none of the internal notes that live in
    # the repo's own ROADMAP.md. Lives with the site rather than being read from the repo root.
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "content", "roadmap.md")
    if not os.path.isfile(path):
        abort(404)
    with open(path, "r", encoding="utf-8") as f:
        content = markdown.markdown(f.read(), extensions=["fenced_code", "tables"])
    return render_template("markdown_page.html", title="Roadmap", content=content)


@app.route("/credits")
def credits():
    return render_template("credits.html")


@app.route("/how-its-built")
def how_its_built():
    content = render_markdown_file("HOW_VERTEX_WORKS.md")
    if content is None:
        abort(404)
    return render_template("markdown_page.html", title="How Vertex Is Built", content=content)


@app.route("/features")
def features():
    content = render_markdown_file("FEATURES.md")
    if content is None:
        abort(404)
    return render_template("markdown_page.html", title="Features", content=content)


@app.route("/downloads/<path:filename>")
def downloads(filename):
    # Only ever serves the client jar - never an arbitrary path under
    # REPO_ROOT, even though filename comes from the URL. The server jar is
    # deliberately not offered here: hosting isn't something to casually
    # advertise as a one-click download on the public site (same reasoning
    # as removing the in-app "Host a Server" client feature - see
    # ROADMAP.md, 2026-09-25). Someone who wants to host reads the GitHub
    # repo's setup instructions instead.
    if filename != "VertexClient.jar":
        abort(404)
    return send_from_directory(REPO_ROOT, filename, as_attachment=True)


if __name__ == "__main__":
    app.run(debug=True)
