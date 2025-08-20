package com.example.BooklyBarbershopBot.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bot_events", indexes = {
        @Index(name = "idx_bot_events_shop_type", columnList = "barbershop_id, event_type")
})
@Getter
@Setter
public class BotEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode eventData;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}