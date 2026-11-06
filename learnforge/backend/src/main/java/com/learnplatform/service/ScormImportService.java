package com.learnplatform.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Reads the text content out of a SCORM (or any HTML) package. This imports content; it is not a SCORM player. */
@Service
public class ScormImportService {
    private static final int MAX_ENTRIES = 600;
    private static final long MAX_TOTAL = 30L * 1024 * 1024;

    public String extractText(byte[] zip) {
        StringBuilder out = new StringBuilder();
        long total = 0;
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw bad("The package has too many files");
                String name = e.getName().toLowerCase(Locale.ROOT);
                if (e.isDirectory() || !(name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml") || name.endsWith(".txt"))) {
                    continue;
                }
                byte[] data = in.readNBytes((int) Math.min(2_000_000, MAX_TOTAL - total));
                total += data.length;
                if (total >= MAX_TOTAL) throw bad("The package is too large once unpacked");
                String text = name.endsWith(".txt") ? new String(data, StandardCharsets.UTF_8)
                        : Jsoup.parse(new String(data, StandardCharsets.UTF_8)).body().text();
                if (text.trim().length() > 40) out.append("## ").append(e.getName()).append("\n").append(text.trim()).append("\n\n");
            }
        } catch (IOException ex) {
            throw bad("Could not read this package");
        }
        if (out.length() < 40) throw bad("No readable content found in this package");
        return out.toString();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
