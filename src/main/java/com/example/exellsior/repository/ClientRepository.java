package com.example.exellsior.repository;

import com.example.exellsior.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByDni(String dni);
   // List<Client> findByDniList(String dni);
   List<Client> findAllByDni(String dni);

    Optional<Client> findFirstByDniOrderByIdDesc(String dni);
    List<Client> findByEntryTimestampBetween(Date from, Date to);
    List<Client> findByDniOrderByIdDesc(String dni);

}
