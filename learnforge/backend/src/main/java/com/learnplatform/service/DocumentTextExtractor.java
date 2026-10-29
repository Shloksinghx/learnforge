package com.learnplatform.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Pulls plain text out of PDF, Word, PowerPoint, Excel and text files. */
@Service
public class DocumentTextExtractor {

    public String extract(String filename, byte[] data) {
        String n = filename == null ? "" : filename.toLowerCase();
        try {
            String text;
            if (n.endsWith(".pdf")) {
                try (PDDocument d = Loader.loadPDF(data)) {
                    text = new PDFTextStripper().getText(d);
                }
            } else if (n.endsWith(".docx")) {
                try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(data));
                     XWPFWordExtractor x = new XWPFWordExtractor(d)) {
                    text = x.getText();
                }
            } else if (n.endsWith(".pptx")) {
                try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(data))) {
                    StringBuilder sb = new StringBuilder();
                    int i = 1;
                    for (XSLFSlide s : ppt.getSlides()) {
                        sb.append("Slide ").append(i++).append("\n");
                        for (XSLFShape sh : s.getShapes()) {
                            if (sh instanceof XSLFTextShape ts) sb.append(ts.getText()).append("\n");
                        }
                        sb.append("\n");
                    }
                    text = sb.toString();
                }
            } else if (n.endsWith(".xlsx")) {
                try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
                    DataFormatter fmt = new DataFormatter();
                    StringBuilder sb = new StringBuilder();
                    for (Sheet sh : wb) {
                        sb.append("Sheet: ").append(sh.getSheetName()).append("\n");
                        for (Row r : sh) {
                            List<String> cells = new ArrayList<>();
                            for (Cell c : r) cells.add(fmt.formatCellValue(c));
                            sb.append(String.join(" | ", cells)).append("\n");
                        }
                    }
                    text = sb.toString();
                }
            } else if (n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".csv")) {
                text = new String(data, StandardCharsets.UTF_8);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported file type. Upload a PDF, DOCX, PPTX, XLSX, TXT, MD or CSV file.");
            }
            String cleaned = text == null ? "" : text.replace("\r", "").replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n{3,}", "\n\n").trim();
            if (cleaned.length() < 40) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No readable text found. Scanned documents need OCR first.");
            }
            return cleaned;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read this file. Is it damaged or password protected?");
        }
    }
}
