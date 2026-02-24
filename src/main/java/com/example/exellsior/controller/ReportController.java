package com.example.exellsior.controller;

import com.example.exellsior.entity.Report;
import com.example.exellsior.services.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    @Autowired
    private ReportService reportService;

    /*@PostMapping
    public ResponseEntity<Report> createReport(@RequestBody Report report) {
        Report savedReport = reportService.saveReport(report);
        return ResponseEntity.ok(savedReport);
    }*/

    // agrega endpoint manual mensual
    @PostMapping("/monthly/generate")
    public ResponseEntity<Report> generateMonthly(@RequestParam(required = false) String month) {
        YearMonth ym = (month == null || month.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(month); // yyyy-MM
        Report r = reportService.generateMonthlyFromDailyReports(ym);
        return ResponseEntity.ok(r);
    }

    @PostMapping
    public ResponseEntity<Report> createOrGenerate(@RequestBody Report report) {
        Report savedReport = reportService.createOrGenerate(report);
        return ResponseEntity.ok(savedReport);
    }



    @GetMapping
    public ResponseEntity<List<Report>> getAllReports() {
        List<Report> reports = reportService.getAllReports();
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getReportById(@PathVariable Long id) {
        Optional<Report> report = reportService.getReportById(id);
        return report.map(r -> ResponseEntity.ok(r))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }
}
