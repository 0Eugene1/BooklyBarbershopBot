package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью Client.
 * Позволяет выполнять операции CRUD и искать клиентов по телефону или telegramId.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    /**
     * Найти клиента по номеру телефона.
     *
     * @param phone номер телефона клиента
     * @return Optional с клиентом, если найден
     */
    Optional<Client> findByPhone(String phone);

    /**
     * Найти клиента по telegramId.
     *
     * @param telegramId идентификатор пользователя Telegram
     * @return Optional с клиентом, если найден
     */
    List<Client> findByTelegramId(Long telegramId);
}
