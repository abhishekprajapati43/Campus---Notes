#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

SRC=.freebuff/index.full.html
[ -f "$SRC" ] || { echo "missing $SRC"; exit 1; }

mkdir -p partials
tmp=.freebuff/chunks
mkdir -p "$tmp"

# ---------- shared partials ----------
sed -n '641,826p'  "$SRC" > partials/sidebar.html   # overlay + sidebar
sed -n '833,1055p' "$SRC" > partials/header.html    # top header
{ sed -n '2119,2180p' "$SRC"; sed -n '2184,2411p' "$SRC"; } > partials/modals.html

# ---------- per-page content chunks ----------
sed -n '1063,1602p' "$SRC" > "$tmp/dash_top.html"      # welcome + quick access + summary + search
sed -n '2084,2115p' "$SRC" > "$tmp/dash_bottom.html"   # resource list
sed -n '1606,1641p' "$SRC" | sed 's/class="hidden card/class="card/' > "$tmp/notifications.html"
sed -n '1642,1681p' "$SRC" | sed 's/class="hidden card/class="card/' > "$tmp/favorites.html"
sed -n '1682,1750p' "$SRC" | sed 's/class="hidden card/class="card/' > "$tmp/settings.html"
sed -n '1751,1819p' "$SRC" | sed 's/class="hidden card/class="card/' > "$tmp/profile.html"
sed -n '1820,2083p' "$SRC" | sed 's/id="adminPanel" class="hidden/id="adminPanel" class="/' > "$tmp/admin.html"

# ---------- page builder ----------
build_page () {
    local out="$1" page="$2" title="$3"
    shift 3
    {
        sed -n '1,42p' "$SRC" | sed "s@<title>[^<]*</title>@<title>${title}</title>@"
        printf '<body data-page="%s">\n' "$page"
        printf '        <div id="dashboardSection" class="min-h-screen">\n'
        printf '            <div id="pageChrome"></div>\n'
        sed -n '827,832p' "$SRC"          # MAIN AREA comment + <main>
        printf '                    <div id="pageHeader"></div>\n'
        sed -n '1056,1062p' "$SRC"        # PAGE CONTENT comment + content div open
        cat "$@"
        printf '                    </div>\n'
        printf '                </main>\n'
        printf '            </div>\n'
        printf '        <script type="module" src="./js/app.js"></script>\n'
        printf '    </body>\n'
        printf '</html>\n'
    } > "$out"
}

build_page dashboard.html      dashboard     "Dashboard | TechCampus_Hub"     "$tmp/dash_top.html" "$tmp/dash_bottom.html"
build_page notifications.html  notifications "Notifications | TechCampus_Hub" "$tmp/notifications.html"
build_page favorites.html      favorites     "Favorites | TechCampus_Hub"     "$tmp/favorites.html"
build_page settings.html       settings      "Settings | TechCampus_Hub"      "$tmp/settings.html"
build_page profile.html        profile       "Profile | TechCampus_Hub"       "$tmp/profile.html"
build_page admin.html          admin         "Admin Panel | TechCampus_Hub"   "$tmp/admin.html"

# ---------- auth page (index.html) ----------
{
    sed -n '1,42p' "$SRC"
    printf '<body data-page="auth">\n'
    sed -n '44,636p' "$SRC"               # authSection + admin login modal
    printf '        <script type="module" src="./js/app.js"></script>\n'
    printf '    </body>\n'
    printf '</html>\n'
} > index.html.tmp
mv index.html.tmp index.html

# ---------- uniform CRLF line endings ----------
perl -i -pe 's/\r?\n\z/\r\n/' index.html dashboard.html notifications.html favorites.html \
    settings.html profile.html admin.html partials/sidebar.html partials/header.html partials/modals.html

echo "split done"
