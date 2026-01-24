package com.example.BooklyBarbershopBot.eventsController;

import com.example.BooklyBarbershopBot.service.eventService.BotEventStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST-контроллер для получения аналитических данных о работе бота.
 * <p>
 * Предоставляет API-конечные точки для интеграции с фронтенд-панелями управления.
 * Позволяет извлекать количественные метрики активности пользователей в разрезе
 * конкретного филиала (барбершопа).
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class BotEventStatsController {

    private final BotEventStatsService statsService;

    /**
     * Формирует агрегированный отчет по ключевым метрикам заведения.
     * <p>
     * Данные включают:
     * <ul>
     * <li>Общее количество уникальных клиентов, взаимодействовавших с ботом.</li>
     * <li>Счетчик успешно завершенных процессов бронирования.</li>
     * <li>Распределение всех действий по типам (клики, выборы услуг, ошибки).</li>
     * </ul>
     *
     * @param barbershopId уникальный UUID филиала.
     * @return {@link StatsResponse} объект, сериализуемый в JSON.
     */
    @GetMapping("/{barbershopId}")
    public StatsResponse getStats(@PathVariable UUID barbershopId) {
        Map<String, Long> eventsByType = statsService.getEventCountsByType(barbershopId);
        Long uniqueUsers = statsService.getUniqueUsersCount(barbershopId);
        Long bookings = statsService.getBookingsCount(barbershopId);

        return new StatsResponse(uniqueUsers, bookings, eventsByType);
    }

    /**
     * Data Transfer Object (DTO) для передачи статистики.
     * * @param uniqueUsers общее кол-во уникальных пользователей.
     * @param bookings кол-во успешных записей.
     * @param eventsByType карта, где ключ — тип события, значение — количество.
     */
    public record StatsResponse(
            Long uniqueUsers,
            Long bookings,
            Map<String, Long> eventsByType
    ) {}
}