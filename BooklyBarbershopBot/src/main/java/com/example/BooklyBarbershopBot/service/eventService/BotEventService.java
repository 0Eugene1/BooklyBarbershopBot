package com.example.BooklyBarbershopBot.service.eventService;

import com.example.BooklyBarbershopBot.entity.BotEvent;
import com.example.BooklyBarbershopBot.repository.BotEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для регистрации и хранения событий взаимодействия пользователя с ботом.
 * <p>
 * Обеспечивает асинхронный (рекомендуется) сбор аналитических данных
 * и логов активности в разрезе конкретных филиалов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotEventService {

    private final BotEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Сохраняет событие в базу данных с поддержкой автоматических повторов при сбоях.
     *
     * @param chatId       уникальный идентификатор чата Telegram.
     * @param barbershopId UUID филиала, в котором произошло событие.
     * @param eventType    тип события (например, "BOOKING_STARTED", "BUTTON_CLICKED").
     * @param eventData    дополнительные метаданные события в виде карты.
     * @throws IllegalArgumentException если chatId или eventType пусты.
     * @throws RuntimeException         при критической ошибке записи.
     */
    @Retryable(value = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void saveEvent(Long chatId, UUID barbershopId, String eventType, Map<String, Object> eventData) {
        if (chatId == null || eventType == null || eventType.trim().isEmpty()) {
            log.error("Некорректные входные данные: chatId или eventType пуст");
            throw new IllegalArgumentException("chatId and eventType must not be null or empty");
        }

        try {
            BotEvent event = new BotEvent();
            event.setChatId(chatId);
            event.setBarbershopId(barbershopId);
            event.setEventType(eventType);
            event.setEventData(convertToJsonb(eventData));

            repository.save(event);
            log.info("Событие сохранено: chatId={}, type={}", chatId, eventType);
        } catch (Exception e) {
            log.error("Ошибка при сохранении события: type={}", eventType, e);
            throw new RuntimeException("Error saving bot event", e);
        }
    }

    /**
     * Извлекает список событий определенного типа для конкретного барбершопа.
     *
     * @param barbershopId UUID филиала.
     * @param eventType    тип искомых событий.
     * @return список сущностей {@link BotEvent}.
     */
    public List<BotEvent> getEventsByType(UUID barbershopId, String eventType) {
        return repository.findByBarbershopIdAndEventType(barbershopId, eventType);
    }

    /**
     * Конвертирует Map с данными в древовидную структуру JSON (JsonNode).
     *
     * @param eventData карта данных.
     * @return объект {@link JsonNode} для сохранения в поле JSONB.
     */
    private JsonNode convertToJsonb(Map<String, Object> eventData) {
        try {
            return eventData != null ? objectMapper.valueToTree(eventData) : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("Ошибка конвертации eventData в JSONB", e);
            return objectMapper.createObjectNode();
        }
    }
}