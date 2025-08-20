package com.example.BooklyBarbershopBot.eventsController;

import com.example.BooklyBarbershopBot.service.eventService.BotEventStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class BotEventStatsController {

    private final BotEventStatsService statsService;

    //Веб-фронтенд или админ-панель делает GET-запрос на /api/stats/{barbershopId}.
    //
    //Контроллер вызывает сервис BotEventStatsService и собирает:
    //
    //количество уникальных пользователей (uniqueUsers)
    //
    //количество успешных бронирований (bookings)
    //
    //количество событий по типам (eventsByType)
    //
    //Всё возвращается в JSON, готовое для фронтенда.

    /**
     * Возвращает статистику по событиям для конкретного барбершопа.
     * Пример URL: /api/stats/123
     */
    @GetMapping("/{barbershopId}")
    public StatsResponse getStats(@PathVariable UUID barbershopId) {
        Map<String, Long> eventsByType = statsService.getEventCountsByType(barbershopId);
        Long uniqueUsers = statsService.getUniqueUsersCount(barbershopId);
        Long bookings = statsService.getBookingsCount(barbershopId);

        return new StatsResponse(uniqueUsers, bookings, eventsByType);
    }

    // DTO для ответа
    public record StatsResponse(Long uniqueUsers, Long bookings, Map<String, Long> eventsByType) {}
}
