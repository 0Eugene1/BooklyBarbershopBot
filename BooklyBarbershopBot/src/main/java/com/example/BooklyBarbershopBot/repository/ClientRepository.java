package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByPhone(String phone);

    Optional<Client> findByTelegramId(Long telegramId);
}
