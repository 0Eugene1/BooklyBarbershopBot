package com.example.BooklyBarbershopBot.service.eventService;

import com.example.BooklyBarbershopBot.repository.BotEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для агрегации и анализа статистических данных бота.
 * <p>
 * Обрабатывает сырые данные о событиях, предоставляя количественные показатели
 * активности пользователей и эффективности процесса бронирования для каждого филиала.
 */
@Service
@RequiredArgsConstructor
public class BotEventStatsService {

    private final BotEventRepository repository;

    /**
     * Формирует карту распределения событий по типам.
     * <p>
     * Позволяет понять, какие функции бота наиболее востребованы (например, просмотр цен,
     * выбор мастера или отмена записи).
     *
     * @param barbershopId уникальный идентификатор филиала.
     * @return Map, где ключ — строковый идентификатор типа события, а значение — общее количество срабатываний.
     */
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
     * Вычисляет охват аудитории бота для конкретного филиала.
     *
     * @param barbershopId идентификатор заведения.
     * @return общее количество уникальных пользователей (по их Telegram chatId).
     */
    public Long getUniqueUsersCount(UUID barbershopId) {
        return repository.countDistinctChatIdByBarbershopId(barbershopId);
    }

    /**
     * Подсчитывает количество успешно созданных записей.
     * <p>
     * Метрика базируется на событии "BOOKING_CREATED", которое генерируется
     * в момент успешного подтверждения записи.
     *
     * @param barbershopId идентификатор заведения.
     * @return количество успешных бронирований за всё время.
     */
    public Long getBookingsCount(UUID barbershopId) {
        return repository.countByBarbershopIdAndEventType(barbershopId, "BOOKING_CREATED");
    }
}