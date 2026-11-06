package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.domain.Course;
import com.learnplatform.domain.Question;
import com.learnplatform.repo.QuestionRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Imports an existing question bank from CSV or XLSX. Imported questions start as DRAFT for review. */
@Service
public class QuestionImportService {
    public static final String TEMPLATE = "question,optionA,optionB,optionC,optionD,correct,explanation,difficulty\n"
            + "\"Which of these is a primary colour?\",Green,Red,Orange,Purple,B,\"Red is a primary colour.\",EASY\n"
            + "\"Select all prime numbers.\",2,4,5,9,\"A,C\",\"2 and 5 are prime.\",MEDIUM\n";
    private static final int MAX_ROWS = 500;

    public record Result(int imported, int skipped) {}

    private final QuestionRepository questions;
    private final ObjectMapper mapper;

    public QuestionImportService(QuestionRepository questions, ObjectMapper mapper) {
        this.questions = questions;
        this.mapper = mapper;
    }

    public Result importFile(Course course, String filename, byte[] data) {
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        List<List<String>> rows;
        if (n.endsWith(".csv")) rows = parseCsv(new String(data, StandardCharsets.UTF_8));
        else if (n.endsWith(".xlsx")) rows = parseXlsx(data);
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a .csv or .xlsx file");
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file has no rows");
        if (rows.size() > MAX_ROWS + 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_ROWS + " questions per file");

        int start = rows.get(0).isEmpty() || !rows.get(0).get(0).trim().equalsIgnoreCase("question") ? 0 : 1;
        int imported = 0;
        int skipped = 0;
        for (int i = start; i < rows.size(); i++) {
            Question q = toQuestion(course, rows.get(i));
            if (q == null) {
                skipped++;
            } else {
                questions.save(q);
                imported++;
            }
        }
        return new Result(imported, skipped);
    }

    private Question toQuestion(Course course, List<String> r) {
        if (r.size() < 6) return null;
        String text = r.get(0).trim();
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String o = r.get(i).trim();
            if (!o.isEmpty()) options.add(o);
        }
        if (text.isEmpty() || options.size() < 2) return null;

        List<Integer> correct = new ArrayList<>();
        for (String part : r.get(5).toUpperCase(Locale.ROOT).split("[,;\\s]+")) {
            if (part.length() == 1 && part.charAt(0) >= 'A' && part.charAt(0) < 'A' + options.size()) {
                int idx = part.charAt(0) - 'A';
                if (!correct.contains(idx)) correct.add(idx);
            }
        }
        if (correct.isEmpty() || correct.size() >= options.size()) return null;

        String level = r.size() > 7 ? r.get(7).trim().toUpperCase(Locale.ROOT) : "MEDIUM";
        boolean multi = correct.size() > 1;
        Question q = new Question();
        q.setOrgId(course.getOrgId());
        q.setCourseId(course.getId());
        q.setText(text);
        try {
            q.setOptionsJson(mapper.writeValueAsString(options));
        } catch (Exception e) {
            return null;
        }
        q.setCorrectIndex(correct.get(0));
        q.setCorrectJson(multi ? QuestionScoring.toJson(correct) : null);
        q.setExplanation(r.size() > 6 ? r.get(6).trim() : "");
        q.setDifficulty(level.equals("EASY") || level.equals("HARD") ? level : "MEDIUM");
        q.setKind(multi ? "MULTI_SELECT" : options.size() == 2 ? "TRUE_FALSE" : "MCQ");
        q.setStatus("DRAFT");
        return q;
    }

    private List<List<String>> parseXlsx(byte[] data) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            DataFormatter fmt = new DataFormatter();
            List<List<String>> out = new ArrayList<>();
            for (Row row : wb.getSheetAt(0)) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    Cell c = row.getCell(i);
                    cells.add(c == null ? "" : fmt.formatCellValue(c));
                }
                out.add(cells);
            }
            return out;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read this spreadsheet");
        }
    }

    /** Small RFC 4180 parser: quoted fields, escaped quotes and line breaks inside quotes. */
    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else cur.append(ch);
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cur.toString());
                cur.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cur.toString());
                cur.setLength(0);
                if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
                row = new ArrayList<>();
            } else {
                cur.append(ch);
            }
        }
        row.add(cur.toString());
        if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
        return rows;
    }
}
