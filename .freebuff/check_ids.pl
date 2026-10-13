use strict;
use warnings;

# Collect ids defined in any html file
my %defined;
for my $f (glob("*.html"), glob("partials/*.html")) {
    open my $fh, '<', $f or next;
    local $/;
    my $txt = <$fh>;
    close $fh;
    $defined{$1} = 1 while $txt =~ /\bid="([^"]+)"/g;
}

# Collect ids referenced from JS: $("id") and getElementById("id")
my %referenced;
for my $f (glob("js/*.js")) {
    open my $fh, '<', $f or next;
    local $/;
    my $txt = <$fh>;
    close $fh;
    $referenced{$1} .= "$f " while $txt =~ /\$\(\s*"([^"]+)"\s*\)/g;
    $referenced{$1} .= "$f " while $txt =~ /getElementById\(\s*"([^"]+)"\s*\)/g;
}

my @missing = sort grep { !$defined{$_} } keys %referenced;
print "defined ids: " . scalar(keys %defined) . "\n";
print "referenced ids: " . scalar(keys %referenced) . "\n";
print "MISSING (referenced but not in any HTML):\n";
print "  $_  <- $referenced{$_}\n" for @missing;
print "(none)\n" unless @missing;
