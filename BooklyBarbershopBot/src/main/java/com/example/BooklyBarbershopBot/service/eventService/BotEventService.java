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

@Service
@RequiredArgsConstructor
@Slf4j
public class BotEventService {

    private final BotEventRepository repository;
    private final ObjectMapper objectMapper;

    @Retryable(value = DataAccessException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void saveEvent(Long chatId, UUID barbershopId, String eventType, Map<String, Object> eventData) {
        if (chatId == null || eventType == null || eventType.trim().isEmpty()) {
            log.error("Invalid input: chatId or eventType is null or empty");
            throw new IllegalArgumentException("chatId and eventType must not be null or empty");
        }

        try {
            BotEvent event = new BotEvent();
            event.setChatId(chatId);
            event.setBarbershopId(barbershopId);
            event.setEventType(eventType);
            event.setEventData(convertToJsonb(eventData));
            repository.save(event);
            log.info("Event saved: chatId={}, barbershopId={}, eventType={}", chatId, barbershopId, eventType);
        } catch (Exception e) {
            log.error("Failed to save event: chatId={}, barbershopId={}, eventType={}", chatId, barbershopId, eventType, e);
            throw new RuntimeException("Error saving bot event", e);
        }
    }

    public List<BotEvent> getEventsByType(UUID barbershopId, String eventType) {
        return repository.findByBarbershopIdAndEventType(barbershopId, eventType);
    }

    private JsonNode convertToJsonb(Map<String, Object> eventData) {
        try {
            return eventData != null ? objectMapper.valueToTree(eventData) : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("Failed to convert eventData to JSONB", e);
            return objectMapper.createObjectNode();
        }
    }
}