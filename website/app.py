"""
Vertex public website - Flask app.

Pages: Home/Changelog, Download (default landing page), Credits, a
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


# A short, curated, human-readable list of highlights - deliberately NOT a
# dump of ROADMAP.md's "Done" section (that's a developer changelog, written
# for someone reading the source; this is a public one). Update this list by
# hand when something worth telling players about ships - it's meant to stay
# short, not track every commit.
CHANGELOG_HIGHLIGHTS = [
    {
        "version": "1.0.0",
        # Named "points", not "items" - a dict already has a real .items()
        # method, so Jinja2's dot-access would silently resolve release.items
        # to that method instead of this key (a well-known Jinja2 gotcha).
        "points": [
            "51 original games, from quick single-player rounds to full online-ranked matches.",
            "Telephone - a new Gartic-Phone-style draw-and-guess party game for 4-8 players.",
            "Every game now opens inside the main window instead of a separate popup.",
            "A real economy: coins, a shop, daily login rewards, and achievements.",
            "Friends, chat, parties, and moderation tools built in.",
            "Security hardening: a filtered network protocol and an integrity-checked auto-updater.",
            "11 visual themes, including a low-end performance mode for older computers.",
        ],
    },
]


@app.route("/")
def download():
    version = get_version()
    return render_template("download.html", version=version)


@app.route("/home")
@app.route("/changelog")
def home():
    version = get_version()
    return render_template("home.html", version=version, highlights=CHANGELOG_HIGHLIGHTS)


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
