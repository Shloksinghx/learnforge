package com.learnplatform.web;

import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.*;
import com.learnplatform.repo.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CertificateController {
    public record IssueReq(@NotNull UUID learnerId, @NotBlank String title,
                           @Min(0) @Max(100) int score, @Min(1) @Max(120) int validityMonths) {}
    public record CertView(String code, String learnerName, String title, int score, String issuer,
                           Instant issuedAt, Instant validUntil, String status) {}

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final CertificateRepository certs;
    private final AppUserRepository users;
    private final OrganisationRepository orgs;
    private final String publicUrl;

    public CertificateController(CertificateRepository certs, AppUserRepository users, OrganisationRepository orgs,
                                 @Value("${app.public-url}") String publicUrl) {
        this.certs = certs;
        this.users = users;
        this.orgs = orgs;
        this.publicUrl = publicUrl;
    }

    @PostMapping("/api/certificates")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','CERTIFICATION_AUTHORITY')")
    public CertView issue(@Valid @RequestBody IssueReq r) {
        var me = CurrentUser.get();
        AppUser learner = users.findById(r.learnerId())
                .filter(u -> u.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learner not found"));
        Certificate c = new Certificate();
        c.setOrgId(me.orgId());
        c.setCode(newCode());
        c.setLearnerId(learner.getId());
        c.setLearnerName(learner.getFullName());
        c.setTitle(r.title());
        c.setScore(r.score());
        c.setIssuer(orgs.findById(me.orgId()).orElseThrow().getName());
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setValidUntil(now.atZone(ZoneOffset.UTC).plusMonths(r.validityMonths()).toInstant());
        certs.save(c);
        return view(c);
    }

    @GetMapping("/api/certificates/mine")
    public List<CertView> mine() {
        return certs.findByLearnerIdOrderByCreatedAtDesc(CurrentUser.get().userId()).stream().map(this::view).toList();
    }

    @PostMapping("/api/certificates/{code}/revoke")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','CERTIFICATION_AUTHORITY')")
    public CertView revoke(@PathVariable String code) {
        Certificate c = certs.findByCode(code)
                .filter(x -> x.getOrgId().equals(CurrentUser.get().orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate not found"));
        c.setRevoked(true);
        return view(certs.save(c));
    }

    @GetMapping("/api/public/certificates/{code}")
    public CertView verify(@PathVariable String code) {
        return certs.findByCode(code).map(this::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate not found"));
    }

    @GetMapping(value = "/api/public/certificates/{code}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String code) throws Exception {
        certs.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate not found"));
        BitMatrix m = new QRCodeWriter().encode(publicUrl + "/verify/" + code, BarcodeFormat.QR_CODE, 320, 320);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(m, "PNG", out);
        return ResponseEntity.ok(out.toByteArray());
    }

    private CertView view(Certificate c) {
        String status = c.isRevoked() ? "REVOKED" : Instant.now().isAfter(c.getValidUntil()) ? "EXPIRED" : "VALID";
        return new CertView(c.getCode(), c.getLearnerName(), c.getTitle(), c.getScore(), c.getIssuer(),
                c.getCreatedAt(), c.getValidUntil(), status);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("LF-");
            for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            String code = sb.toString();
            if (certs.findByCode(code).isEmpty()) return code;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not allocate certificate code");
    }
}
