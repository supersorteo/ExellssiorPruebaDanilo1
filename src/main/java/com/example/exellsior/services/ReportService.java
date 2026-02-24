package com.example.exellsior.services;

import com.example.exellsior.entity.Client;
import com.example.exellsior.entity.Report;
import com.example.exellsior.repository.ClientRepository;
import com.example.exellsior.repository.ReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.exellsior.repository.SpaceRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.*;

@Service
public class ReportService {
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private SpaceRepository spaceRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    public Report saveReport0(Report report) {
        return reportRepository.save(report);
    }

    public Report saveReport(Report report) {
        // si frontend no envía periodType/key, completar como DAILY
        if (report.getPeriodType() == null || report.getPeriodType().isBlank()) {
            report.setPeriodType("DAILY");
        }
        if (report.getPeriodKey() == null || report.getPeriodKey().isBlank()) {
            String dayKey = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            report.setPeriodKey(dayKey);
        }
        return reportRepository.save(report);
    }

    public List<Report> getAllReports() {
        return reportRepository.findAll();
    }

    public Optional<Report> getReportById(Long id) {
        return reportRepository.findById(id);
    }

    public void deleteReport(Long id) {
        reportRepository.deleteById(id);
    }



    /*public Report generateMonthlyReport(LocalDate monthDate) {
        LocalDate firstDay = monthDate.withDayOfMonth(1);
        LocalDate nextMonth = firstDay.plusMonths(1);

        Date from = Date.from(firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date to = Date.from(nextMonth.atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<Client> monthlyClients = clientRepository.findByEntryTimestampBetween(from, to);

        Map<String, Long> paymentAmounts = new LinkedHashMap<>();
        paymentAmounts.put("efectivo", 0L);
        paymentAmounts.put("credito", 0L);
        paymentAmounts.put("prepago", 0L);
        paymentAmounts.put("qr", 0L);
        paymentAmounts.put("debito", 0L);
        paymentAmounts.put("scaneo", 0L);
        paymentAmounts.put("S/Cargo", 0L);
        paymentAmounts.put("otros", 0L);

        long totalCobrado = 0L;
        for (Client c : monthlyClients) {
            long amount = c.getPrice() != null ? c.getPrice() : 0;
            totalCobrado += amount;
            String method = c.getPaymentMethod() != null ? c.getPaymentMethod().trim().toLowerCase() : "otros";
            if (!paymentAmounts.containsKey(method)) method = "otros";
            paymentAmounts.put(method, paymentAmounts.get(method) + amount);
        }

        int totalSpaces = (int) spaceRepository.count();
        int occupiedSpaces = 0; // mensual acumulado: snapshot no aplica, puedes guardar 0 o estado actual
        int freeSpaces = totalSpaces - occupiedSpaces;
        int occupancyRate = totalSpaces > 0 ? Math.round((occupiedSpaces * 100f) / totalSpaces) : 0;

        Report report = new Report();
        report.setTimestamp(OffsetDateTime.now().toString());
        report.setPeriodType("MONTHLY");
        report.setPeriodKey(firstDay.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        report.setTotalSpaces(totalSpaces);
        report.setOccupiedSpaces(occupiedSpaces);
        report.setFreeSpaces(freeSpaces);
        report.setOccupancyRate(occupancyRate);
        report.setSubsueloStats("[]");
        report.setTimeStats("{}");
        report.setTotalCobrado(totalCobrado);

        try {
            report.setFilteredClients(mapper.writeValueAsString(monthlyClients));
            report.setPaymentAmounts(mapper.writeValueAsString(paymentAmounts));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando reporte mensual", e);
        }

        return reportRepository.save(report);
    }

    // Ejecuta diariamente a las 23:59 y genera mensual solo si es último día del mes
    @Scheduled(cron = "0 59 23 * * *", zone = "America/Argentina/Buenos_Aires")
    public void generateMonthlyReportIfLastDay() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        LocalDate tomorrow = today.plusDays(1);

        if (!tomorrow.getMonth().equals(today.getMonth())) {
            String key = today.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            boolean alreadyExists = reportRepository.existsByPeriodTypeAndPeriodKey("MONTHLY", key);
            if (!alreadyExists) {
                generateMonthlyReport(today);
            }
        }
    }*/




    public Report generateMonthlyFromDailyReports(YearMonth ym) {
        String monthKey = ym.toString(); // yyyy-MM

        List<Report> dailyReports = reportRepository
                .findByPeriodTypeAndPeriodKeyStartingWith("DAILY", monthKey);

        List<Map<String, Object>> monthlyClients = new ArrayList<>();
        Map<String, Long> paymentAmounts = new LinkedHashMap<>();
        paymentAmounts.put("efectivo", 0L);
        paymentAmounts.put("credito", 0L);
        paymentAmounts.put("prepago", 0L);
        paymentAmounts.put("qr", 0L);
        paymentAmounts.put("debito", 0L);
        paymentAmounts.put("scaneo", 0L);
        paymentAmounts.put("S/Cargo", 0L);
        paymentAmounts.put("otros", 0L);

        long totalCobrado = 0L;
        int totalSpaces = 0;
        int occupiedSpaces = 0;
        int freeSpaces = 0;
        int occupancyRate = 0;

        for (Report d : dailyReports) {
            try {
                List<Map<String, Object>> dayClients = mapper.readValue(
                        d.getFilteredClients(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                monthlyClients.addAll(dayClients);
            } catch (Exception e) {
                System.out.println("Error parseando filteredClients del reporte diario ID " + d.getId());
            }

            try {
                Map<String, Object> dayPay = mapper.readValue(
                        d.getPaymentAmounts() == null ? "{}" : d.getPaymentAmounts(),
                        new TypeReference<Map<String, Object>>() {}
                );
                for (Map.Entry<String, Object> entry : dayPay.entrySet()) {
                    String k = entry.getKey();
                    long v = Long.parseLong(String.valueOf(entry.getValue()));
                    if (!paymentAmounts.containsKey(k)) k = "otros";
                    paymentAmounts.put(k, paymentAmounts.get(k) + v);
                }
            } catch (Exception ignored) {}

            totalCobrado += (d.getTotalCobrado() == null ? 0L : d.getTotalCobrado());

            // snapshot del último diario del mes
            totalSpaces = d.getTotalSpaces();
            occupiedSpaces = d.getOccupiedSpaces();
            freeSpaces = d.getFreeSpaces();
            occupancyRate = d.getOccupancyRate();
        }

        Report monthly = reportRepository
                .findByPeriodTypeAndPeriodKey("MONTHLY", monthKey)
                .orElse(new Report());

        monthly.setTimestamp(OffsetDateTime.now().toString());
        monthly.setPeriodType("MONTHLY");
        monthly.setPeriodKey(monthKey);
        monthly.setTotalSpaces(totalSpaces);
        monthly.setOccupiedSpaces(occupiedSpaces);
        monthly.setFreeSpaces(freeSpaces);
        monthly.setOccupancyRate(occupancyRate);

        try {
            monthly.setFilteredClients(mapper.writeValueAsString(monthlyClients));
            monthly.setPaymentAmounts(mapper.writeValueAsString(paymentAmounts));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando reporte mensual", e);
        }

        monthly.setSubsueloStats("[]");
        monthly.setTimeStats("{}");
        monthly.setTotalCobrado(totalCobrado);

        return reportRepository.save(monthly);
    }

    public Report createOrGenerate(Report report) {
        String periodType = (report.getPeriodType() == null || report.getPeriodType().isBlank())
                ? "DAILY"
                : report.getPeriodType().trim().toUpperCase();

        if ("MONTHLY".equals(periodType)) {
            YearMonth ym = resolveYearMonth(report);
            return generateMonthlyFromDailyReports(ym);
        }

        // DAILY
        report.setPeriodType("DAILY");
        if (report.getPeriodKey() == null || report.getPeriodKey().isBlank()) {
            report.setPeriodKey(LocalDate.now().toString()); // yyyy-MM-dd
        }
        if (report.getTimestamp() == null || report.getTimestamp().isBlank()) {
            report.setTimestamp(OffsetDateTime.now().toString());
        }
        return reportRepository.save(report);
    }

    private YearMonth resolveYearMonth(Report report) {
        if (report.getPeriodKey() != null && !report.getPeriodKey().isBlank()) {
            return YearMonth.parse(report.getPeriodKey()); // yyyy-MM
        }
        if (report.getTimestamp() != null && !report.getTimestamp().isBlank()) {
            return YearMonth.from(OffsetDateTime.parse(report.getTimestamp()));
        }
        return YearMonth.now();
    }

    @Scheduled(cron = "0 59 23 * * *", zone = "America/Argentina/Buenos_Aires")
    public void autoMonthlyEndOfMonth() {
        ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate now = LocalDate.now(zone);
        LocalDate tomorrow = now.plusDays(1);

        boolean isEndOfMonth = now.getMonthValue() != tomorrow.getMonthValue();

        System.out.println("[MONTHLY-CHECK] now=" + now +
                " tomorrow=" + tomorrow +
                " isEndOfMonth=" + isEndOfMonth);

        if (!isEndOfMonth) return;

        YearMonth ym = YearMonth.from(now);
        System.out.println("[MONTHLY-GENERATE] Generando mensual para " + ym);
        generateMonthlyFromDailyReports(ym);
    }



}
