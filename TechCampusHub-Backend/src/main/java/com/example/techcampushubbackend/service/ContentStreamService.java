package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.config.AppProperties;
import com.example.techcampushubbackend.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Secure PDF relay for the in-app viewer: fetches the stored file from
 * Cloudinary server-side and streams the bytes to the caller. The storage URL
 * never leaves this process, so a student's browser only ever sees the
 * same-origin {@code /api/content/**} path - which requires the JWT on every
 * request and can therefore not be opened by a bare navigation, an iframe, or
 * a shared link.
 *
 * <p>SSRF guard: only hosts on the {@code app.content.stream-hosts} allowlist
 * (default {@code res.cloudinary.com}) are fetched, and plain {@code http} is
 * additionally restricted to loopback addresses so the allowlist can never be
 * used to reach arbitrary services unencrypted.
 */
@Slf4j
@Service
public class ContentStreamService {

    /** Suffix-matched host allowlist, e.g. ["res.cloudinary.com"]. */
    private final List<String> allowedHosts;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ContentStreamService(AppProperties appProperties) {
        this.allowedHosts = appProperties.getContent().getStreamHosts().stream()
                .filter(host -> host != null && !host.isBlank())
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Relays the PDF at {@code sourceUrl} as an inline, non-cacheable response.
     * Every failure before the response is committed is raised as an ApiException
     * so clients still receive the standard JSON error body.
     */
    public void stream(String sourceUrl, HttpServletResponse response) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No file is available for this item");
        }
        if (!isAllowedSource(sourceUrl)) {
            log.warn("Blocked content stream to non-allowlisted source");
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document storage location is not allowed");
        }

        HttpResponse<InputStream> upstream = fetch(sourceUrl);
        int status = upstream.statusCode();
        if (status == 404 || status == 401 || status == 403) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found in storage");
        }
        if (status != 200) {
            log.warn("Content stream upstream answered with status {}", status);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document could not be loaded right now");
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline");
        // Never let a browser or shared cache hold relayed document bytes.
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");

        try (InputStream in = upstream.body()) {
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            // Viewer closed mid-transfer - there is nothing left to send.
            log.debug("Content stream cut short: {}", e.getMessage());
        }
    }

    private HttpResponse<InputStream> fetch(String sourceUrl) {
        try {
            return httpClient.send(
                    HttpRequest.newBuilder(URI.create(sourceUrl))
                            .timeout(Duration.ofSeconds(60))
                            .header("Accept", "application/pdf, */*")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("Content stream fetch failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document could not be loaded right now");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document could not be loaded right now");
        }
    }

    /**
     * One-shot, allowlisted download used by the PDF search indexer: same
     * SSRF guard as the viewer relay, but the bytes are handed back to the
     * caller instead of being streamed to a response.
     */
    public byte[] fetchBytes(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No file is available for this item");
        }
        if (!isAllowedSource(sourceUrl)) {
            log.warn("Blocked content fetch to non-allowlisted source");
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document storage location is not allowed");
        }

        try {
            HttpResponse<byte[]> upstream = httpClient.send(
                    HttpRequest.newBuilder(URI.create(sourceUrl))
                            .timeout(Duration.ofSeconds(60))
                            .header("Accept", "application/pdf, */*")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            int status = upstream.statusCode();
            if (status != 200) {
                log.warn("Content fetch upstream answered with status {}", status);
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Document could not be loaded right now");
            }
            return upstream.body();
        } catch (IOException e) {
            log.warn("Content fetch failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document could not be loaded right now");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document could not be loaded right now");
        }
    }

    /** True when the URL is https on the allowlist (or http on loopback). */
    private boolean isAllowedSource(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean hostAllowed = allowedHosts.stream().anyMatch(
                allowed -> normalizedHost.equals(allowed)
                        || normalizedHost.endsWith("." + allowed));
        if (!hostAllowed) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        return "http".equalsIgnoreCase(uri.getScheme()) && isLoopback(normalizedHost);
    }

    private static boolean isLoopback(String host) {
        return host.equals("localhost")
                || host.startsWith("127.")
                || host.equals("[::1]");
    }
}
