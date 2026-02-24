package com.example.exellsior.repository;

import com.example.exellsior.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByPeriodTypeAndPeriodKey(String periodType, String periodKey);
    Optional<Report> findByPeriodTypeAndPeriodKey(String periodType, String periodKey);
    List<Report> findByPeriodTypeAndPeriodKeyStartingWith(String periodType, String periodPrefix);

}
