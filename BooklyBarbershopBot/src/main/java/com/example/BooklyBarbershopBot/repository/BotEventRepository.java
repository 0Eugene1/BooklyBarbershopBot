package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.BotEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Репозиторий для анализа поведенческих данных и системных событий.
 * <p>
 * Предоставляет методы для агрегации статистики, которая используется
 * в административных отчетах и дашбордах мониторинга.
 */
@Repository
public interface BotEventRepository extends JpaRepository<BotEvent, Long> {

    /**
     * Возвращает список всех событий конкретного типа для филиала.
     */
    List<BotEvent> findByBarbershopIdAndEventType(UUID barbershopId, String eventType);

    /**
     * Группирует все события филиала по их типам и подсчитывает количество каждого.
     * <p>
     * Результат содержит массив объектов, где:
     * [0] - String (eventType)
     * [1] - Long (count)
     *
     * @param barbershopId идентификатор заведения.
     * @return список пар "Тип события - Количество".
     */
    @Query("SELECT e.eventType, COUNT(e) FROM BotEvent e WHERE e.barbershopId = :barbershopId GROUP BY e.eventType")
    List<Object[]> getEventCounts(@Param("barbershopId") UUID barbershopId);

    /**
     * Подсчитывает общее количество событий определенного типа.
     */
    Long countByBarbershopIdAndEventType(UUID barbershopId, String eventType);

    /**
     * Вычисляет количество уникальных пользователей, взаимодействовавших с филиалом.
     * <p>
     * Использует {@code DISTINCT} по {@code chatId}, чтобы исключить повторные
     * срабатывания от одного и того же клиента.
     */
    @Query("SELECT COUNT(DISTINCT e.chatId) FROM BotEvent e WHERE e.barbershopId = :barbershopId")
    Long countDistinctChatIdByBarbershopId(@Param("barbershopId") UUID barbershopId);
}