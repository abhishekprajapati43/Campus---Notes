use strict;
use warnings;
use IO::Socket::INET;
use File::Spec;

my $root = $ARGV[0] || '.';
my $port = $ARGV[1] || 8080;

my %mime = (
    'html' => 'text/html; charset=utf-8',
    'htm'  => 'text/html; charset=utf-8',
    'js'   => 'text/javascript; charset=utf-8',
    'mjs'  => 'text/javascript; charset=utf-8',
    'css'  => 'text/css; charset=utf-8',
    'json' => 'application/json; charset=utf-8',
    'svg'  => 'image/svg+xml',
    'png'  => 'image/png',
    'jpg'  => 'image/jpeg',
    'jpeg' => 'image/jpeg',
    'gif'  => 'image/gif',
    'ico'  => 'image/x-icon',
    'webp' => 'image/webp',
    'pdf'  => 'application/pdf',
    'txt'  => 'text/plain; charset=utf-8',
    'woff' => 'font/woff',
    'woff2'=> 'font/woff2',
);

my $srv = IO::Socket::INET->new(
    LocalAddr => '127.0.0.1',
    LocalPort => $port,
    Proto     => 'tcp',
    Listen    => 10,
    ReuseAddr => 1,
) or die "cannot listen on $port: $!\n";

print "serving $root on http://127.0.0.1:$port/\n";

while (my $c = $srv->accept()) {
    eval {
        local $SIG{ALRM} = sub { die "timeout\n" };
        alarm(10);

        my $request_line = <$c>;
        next unless defined $request_line;

        # drain headers
        while (my $h = <$c>) {
            last if $h =~ /^\r?\n$/;
        }
        alarm(0);

        chomp $request_line;
        $request_line =~ s/\r$//;

        my ($method, $target) = split /\s+/, $request_line;
        if (!$target) { close $c; next; }

        $target =~ s/\?.*$//;
        $target =~ s/%([0-9A-Fa-f]{2})/chr(hex($1))/eg;
        $target =~ s#/+$##;
        $target = '/index.html' if $target eq '' || $target eq '/';

        my $file = File::Spec->catfile($root, split m#/#, $target);

        if ($file =~ /\.\./ || !-f $file) {
            my $body = "404 Not Found\n";
            print $c "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: " . length($body) . "\r\nConnection: close\r\n\r\n$body";
            close $c;
            next;
        }

        my ($ext) = $file =~ /\.([A-Za-z0-9]+)$/;
        $ext = defined $ext ? lc $ext : 'txt';
        my $type = $mime{$ext} || 'application/octet-stream';

        open my $fh, '<:raw', $file or die "open $file: $!\n";
        local $/;
        my $data = <$fh>;
        close $fh;

        print $c "HTTP/1.1 200 OK\r\n"
              . "Content-Type: $type\r\n"
              . "Content-Length: " . length($data) . "\r\n"
              . "Cache-Control: no-cache\r\n"
              . "Connection: close\r\n\r\n";
        print $c $data;
        close $c;
    };
    if ($@) {
        eval { close $c; };
    }
}
