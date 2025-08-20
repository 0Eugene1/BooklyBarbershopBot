package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.BotEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BotEventRepository extends JpaRepository<BotEvent, Long> {

    List<BotEvent> findByBarbershopIdAndEventType(UUID barbershopId, String eventType);

    @Query("SELECT e.eventType, COUNT(e) FROM BotEvent e WHERE e.barbershopId = :barbershopId GROUP BY e.eventType")
    List<Object[]> getEventCounts(@Param("barbershopId") UUID barbershopId);

    Long countByBarbershopIdAndEventType(UUID barbershopId, String eventType);

    @Query("SELECT COUNT(DISTINCT e.chatId) FROM BotEvent e WHERE e.barbershopId = :barbershopId")
    Long countDistinctChatIdByBarbershopId(@Param("barbershopId") UUID barbershopId);
}