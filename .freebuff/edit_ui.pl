#!/usr/bin/perl
# Verified in-place edits for js/ui.js (multi-page split).
use strict;
use warnings;

my $file = 'js/ui.js';
open my $fh, '<:raw', $file or die "open $file: $!";
my @lines = <$fh>;
close $fh;

sub find_line {
    my ($re) = @_;
    for my $i (0 .. $#lines) {
        my $l = $lines[$i];
        $l =~ s/\r?\n\z//;
        return $i if $l =~ $re;
    }
    die "pattern not found: $re";
}

sub splice_lines {
    my ($start, $count, $text) = @_;
    my @new = split /(?<=\n)/, $text;
    splice(@lines, $start, $count, @new);
}

# -------------------------------------------------
# 1) initializeApp: auth-screen setup only on login page
# -------------------------------------------------
{
    my $i = find_line(qr/^export async function initializeApp\(\) \{$/);
    die "E1 showAuthSection" unless $lines[$i + 2] =~ /showAuthSection\(\);/;
    die "E1 initRegistration" unless $lines[$i + 10] =~ /initializeRegistrationFields\(\);/;
    splice_lines($i, 11, <<'EOF');
export async function initializeApp() {

    // The auth screen only exists on the login page.
    if (currentPage() === "auth") {

        showAuthSection();

        updateAuthModeUI();

        initializeRegistrationFields();

    }
EOF
}

# -------------------------------------------------
# 2) applyRoleUI tail: refresh the admin nav link
# -------------------------------------------------
{
    my $i = find_line(qr/^updateAllSearchSubjects\(\);$/);
    die "E2 blank" unless $lines[$i + 1] =~ /^\r?$/;
    die "E2 brace"  unless $lines[$i + 2] =~ /^\}\r?\n?$/;
    splice_lines($i + 2, 1, "    updateAdminNavVisibility();\n}\n");
}

# -------------------------------------------------
# 3) applyRoleUI head: page-based redirects
# -------------------------------------------------
{
    my $i = find_line(qr/^export function applyRoleUI\(\) \{$/);
    splice_lines($i, 1, <<'EOF');
export function applyRoleUI() {
    const page = currentPage();

    // Login page: after a successful login go to the right home.
    if (page === "auth") {
        window.location.replace(
            isAdminRole() ? "./admin.html" : "./dashboard.html"
        );
        return;
    }

    // The admin area is only for admins.
    if (page === "admin" && !isAdminRole()) {
        window.location.replace("./dashboard.html");
        return;
    }

EOF
}

# -------------------------------------------------
# 4) updateAdminLoginButton -> updateAdminNavVisibility
# -------------------------------------------------
{
    my $i = find_line(qr/^export function updateAdminLoginButton\(\) \{$/);
    my $end = $i;
    $end++ while $end <= $#lines && $lines[$end] !~ /^\}\r?\n?$/;
    die "E4 no close" if $end > $i + 30;
    splice_lines($i, $end - $i + 1, <<'EOF');
export function updateAdminNavVisibility() {
    const navLink = $("adminNavBtn");

    if (!navLink) {
        return;
    }

    navLink.classList.toggle(
        "hidden",
        !isAdminRole()
    );
}
EOF

    # Update the section comment above it too.
    my $c = find_line(qr/^\/\/ Admin Login Button$/);
    $lines[$c] = "// Admin nav visibility\n";
}

# -------------------------------------------------
# 5) currentPage() helper before showAuthSection
# -------------------------------------------------
{
    my $i = find_line(qr/^export function showAuthSection\(\) \{$/);
    splice_lines($i, 0, <<'EOF');
// Current route: "auth", "dashboard", "notifications", ...
function currentPage() {
    return document.body?.dataset?.page || "auth";
}

EOF
}

# -------------------------------------------------
# 6) logoutUser: returning to login leaves the page
# -------------------------------------------------
{
    my $i;
    for my $k (0 .. $#lines - 1) {
        my $a = $lines[$k];
        my $b = $lines[$k + 1];
        $a =~ s/\r?\n\z//;
        $b =~ s/\r?\n\z//;
        if ($a =~ /^\s*hideElement\(\$\("adminPanel"\)\);\s*$/
            && $b =~ /^\s*showAuthSection\(\);\s*$/) {
            $i = $k;
            last;
        }
    }
    die "E6 anchor not found" unless defined $i;
    splice_lines($i + 2, 0, <<'EOF');

        // Logging out from a protected page returns to login.
        if (currentPage() !== "auth") {
            window.location.replace("./index.html");
            return;
        }
EOF
}

my $out = join '', @lines;
$out =~ s/\r?\n/\r\n/g;
open my $ofh, '>:raw', $file or die "write $file: $!";
print {$ofh} $out;
close $ofh;
print "ui.js edits applied\n";
