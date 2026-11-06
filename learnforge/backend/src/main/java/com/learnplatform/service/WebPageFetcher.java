package com.learnplatform.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Fetches a public web page and extracts its readable text. Refuses private and internal addresses. */
@Service
public class WebPageFetcher {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(8)).build();

    public String fetchText(String url) {
        URI uri = validate(parse(url));
        try {
            for (int hop = 0; hop < 4; hop++) {
                HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                        .header("User-Agent", "LearnForgeBot/1.0").GET().build();
                HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int code = res.statusCode();
                if (code >= 300 && code < 400) {
                    String loc = res.headers().firstValue("Location").orElse(null);
                    res.body().close();
                    if (loc == null) break;
                    uri = validate(uri.resolve(loc));
                    continue;
                }
                if (code != 200) throw bad("The page answered with status " + code);
                String type = res.headers().firstValue("Content-Type").orElse("").toLowerCase();
                if (!type.contains("html") && !type.contains("text/plain")) {
                    res.body().close();
                    throw bad("That link is not a web page or text file");
                }
                String body;
                try (InputStream in = res.body()) {
                    body = new String(in.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
                }
                return type.contains("html") ? htmlToText(body) : body.trim();
            }
        } catch (IOException e) {
            throw bad("Could not fetch that page");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw bad("Could not fetch that page");
        }
        throw bad("Too many redirects");
    }

    private static String htmlToText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script,style,nav,footer,header,aside,form,noscript,svg").remove();
        String text = doc.select("h1,h2,h3,h4,p,li,td,th").stream()
                .map(e -> e.text().trim()).filter(t -> !t.isEmpty()).collect(Collectors.joining("\n"));
        return text.isBlank() ? doc.body().text() : text;
    }

    private static URI parse(String url) {
        try {
            return URI.create(url.trim());
        } catch (Exception e) {
            throw bad("That is not a valid web address");
        }
    }

    private static URI validate(URI u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if ((!scheme.equals("http") && !scheme.equals("https")) || u.getHost() == null) {
            throw bad("Enter a full http or https address");
        }
        try {
            for (InetAddress a : InetAddress.getAllByName(u.getHost())) {
                byte[] b = a.getAddress();
                boolean carrierNat = b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
                boolean uniqueLocal = b.length == 16 && (b[0] & 0xfe) == 0xfc;
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                        || a.isMulticastAddress() || carrierNat || uniqueLocal) {
                    throw bad("That address is not allowed");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw bad("Could not find that website");
        }
        return u;
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
