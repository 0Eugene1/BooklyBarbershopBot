package com.example.BooklyBarbershopBot.service.eventService;

import com.example.BooklyBarbershopBot.repository.BotEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotEventStatsService {

    private final BotEventRepository repository;

    /**
     * Возвращает статистику событий для конкретного барбершопа.
     *
     * @param barbershopId ID барбершопа
     * @return Map<EventType, Count>
     */

    //Метод getEventCountsByType возвращает Map<String, Long>,
    // где ключ — тип события (USER_STARTED, BOOKING_CREATED и т.д.),
    // а значение — количество таких событий для конкретного барбершопа.
    public Map<String, Long> getEventCountsByType(UUID barbershopId) {
        List<Object[]> results = repository.getEventCounts(barbershopId);

        Map<String, Long> stats = new HashMap<>();
        for (Object[] row : results) {
            String eventType = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            stats.put(eventType, count);
        }
        return stats;
    }
    /**
     * Количество уникальных пользователей (chat_id) для конкретного барбершопа.
     */
    public Long getUniqueUsersCount(UUID barbershopId) {
        return repository.countDistinctChatIdByBarbershopId(barbershopId);
    }

    /**
     * Количество успешных бронирований (например, событие BOOKING_CREATED).
     */
    public Long getBookingsCount(UUID barbershopId) {
        return repository.countByBarbershopIdAndEventType(barbershopId, "BOOKING_CREATED");
    }
}
