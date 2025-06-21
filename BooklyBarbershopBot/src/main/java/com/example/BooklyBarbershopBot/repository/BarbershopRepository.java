package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BarbershopRepository extends JpaRepository<Barbershop, UUID> {
    Optional<Barbershop> findByTelegramSlug(String findByTelegramSlug);
}
