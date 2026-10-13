#!/usr/bin/perl
# Verified in-place edits for js/events.js (remove SPA page switching).
use strict;
use warnings;

my $file = 'js/events.js';
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
# 1) Remove SPA page-switching block; keep only the
#    notification preference inside setupStudentSidebarEvents.
# -------------------------------------------------
{
    my $b = find_line(qr/^\/\/ STUDENT SIDEBAR PAGE SWITCHING$/);
    die "E-B prev comment" unless $lines[$b - 1] =~ /={10,}/;
    my $n = find_line(qr/^    \/\/ Notification preference \(stored locally\)$/);
    die "E-B range" unless $n > $b;

    splice_lines($b - 1, $n - ($b - 1), <<'EOF');
// =====================================================
// STUDENT SIDEBAR
// (page navigation is plain links between pages; only
// local preferences are handled here)
// =====================================================

function setupStudentSidebarEvents() {

EOF
}

# -------------------------------------------------
# 2) handleCategoryButtonClick: drop the view switch,
#    keep card active state and mirror it on sidebar links.
# -------------------------------------------------
{
    my $a = find_line(qr/Category selection always returns to the dashboard view\./);
    die "E-A next" unless $lines[$a + 1] =~ /showStudentDashboard\(\{/;
    die "E-A end"  unless $lines[$a + 14] =~ /^        \}\);\r?\n?$/;

    splice_lines($a, 15, <<'EOF');
    document
        .querySelectorAll(
            "[data-dashboard-category]"
        )
        .forEach(item => {
            item.classList.toggle(
                "active",
                item === button
            );
        });

    // Sidebar category links mirror the selection.
    document
        .querySelectorAll(
            "[data-nav-category]"
        )
        .forEach(item => {
            item.classList.toggle(
                "active",
                item.dataset.navCategory === category
            );
        });
EOF
}

my $out = join '', @lines;
$out =~ s/\r?\n/\r\n/g;
open my $ofh, '>:raw', $file or die "write $file: $!";
print {$ofh} $out;
close $ofh;
print "events.js edits applied\n";
