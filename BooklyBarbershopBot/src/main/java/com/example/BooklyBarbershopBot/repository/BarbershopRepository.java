package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Интерфейс репозитория для управления персистентным состоянием филиалов.
 * <p>
 * Обеспечивает связующее звено между объектной моделью {@link Barbershop}
 * и реляционной таблицей в базе данных.
 */
@Repository
public interface BarbershopRepository extends JpaRepository<Barbershop, UUID> {

    /**
     * Выполняет поиск филиала по его текстовому идентификатору (Deep Link slug).
     * <p>
     * Данный метод критически важен для маршрутизации входящих запросов
     * при обработке команды {@code /start slug}.
     *
     * @param telegramSlug уникальное имя филиала в системе Telegram.
     * @return {@link Optional}, содержащий объект барбершопа, если он зарегистрирован.
     */
    Optional<Barbershop> findByTelegramSlug(String telegramSlug);
}