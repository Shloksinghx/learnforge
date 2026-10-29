package com.learnplatform.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.AppUser;
import com.learnplatform.domain.Certificate;
import com.learnplatform.repo.AppUserRepository;
import com.learnplatform.repo.CertificateRepository;
import com.learnplatform.service.CertificatePdfService;
import com.learnplatform.service.CertificateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
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

    private final CertificateRepository certs;
    private final AppUserRepository users;
    private final CertificateService service;
    private final CertificatePdfService pdf;
    private final String publicUrl;

    public CertificateController(CertificateRepository certs, AppUserRepository users, CertificateService service,
                                 CertificatePdfService pdf, @Value("${app.public-url}") String publicUrl) {
        this.pdf = pdf;
        this.certs = certs;
        this.users = users;
        this.service = service;
        this.publicUrl = publicUrl;
    }

    @PostMapping("/api/certificates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','CERTIFICATION_AUTHORITY')")
    public CertView issue(@Valid @RequestBody IssueReq r) {
        var me = CurrentUser.get();
        AppUser learner = users.findById(r.learnerId())
                .filter(u -> u.getOrgId().equals(me.orgId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learner not found"));
        return view(service.issue(learner, r.title(), r.score(), r.validityMonths()));
    }

    @GetMapping("/api/certificates/mine")
    public List<CertView> mine() {
        return certs.findByLearnerIdOrderByCreatedAtDesc(CurrentUser.get().userId()).stream().map(this::view).toList();
    }

    @PostMapping("/api/certificates/{code}/revoke")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORG_ADMIN','CERTIFICATION_AUTHORITY')")
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
        return ResponseEntity.ok(qrPng(code));
    }

    @GetMapping(value = "/api/public/certificates/{code}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String code) throws Exception {
        Certificate c = certs.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate not found"));
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"certificate-" + code + ".pdf\"")
                .body(pdf.render(c, qrPng(code)));
    }

    private byte[] qrPng(String code) throws Exception {
        BitMatrix m = new QRCodeWriter().encode(publicUrl + "/verify/" + code, BarcodeFormat.QR_CODE, 320, 320);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(m, "PNG", out);
        return out.toByteArray();
    }

    private CertView view(Certificate c) {
        String status = c.isRevoked() ? "REVOKED" : Instant.now().isAfter(c.getValidUntil()) ? "EXPIRED" : "VALID";
        return new CertView(c.getCode(), c.getLearnerName(), c.getTitle(), c.getScore(), c.getIssuer(),
                c.getCreatedAt(), c.getValidUntil(), status);
    }
}
