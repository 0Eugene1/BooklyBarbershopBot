package com.example.BooklyBarbershopBot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность Booking — представляет запись (бронь) на услугу в барбершопе.
 * <p>
 * Содержит информацию о дате и времени записи, выбранном мастере и услуге,
 * статусе записи, а также связанном клиенте и идентификаторах для интеграции с Yclients.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    /**
     * Уникальный идентификатор записи в базе данных.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * Дата и время записи в формате строки (ISO или аналогичном).
     */
    @Column(name = "datetime", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime datetime;
    /**
     * Имя мастера, на которого сделана запись.
     */
    private String staffName;
    /**
     * Название услуги, на которую сделана запись.
     */
    private String serviceName;
    /**
     * Уникальный слаг (slug) барбершопа, к которому относится запись.
     */
    private String slug;
    /**
     * Клиент, сделавший запись.
     */
    @ManyToOne
    private Client client;
    /**
     * Идентификатор записи в системе Yclients.
     */
    private Long recordId; // ID записи в Yclients

    /**
     * Хэш для управления записью в Yclients (например, для отмены или изменения).
     */
    private String recordHash;// Hash для управления записью
    /**
     * Статус записи (например, PENDING, CONFIRMED, CANCELLED).
     */
    private String status;      // PENDING, CONFIRMED, CANCELLED
    /**
     * Идентификатор мастера в системе Yclients.
     */
    private Long staffId;
    /**
     * Идентификатор услуги в системе Yclients.
     */
    private Long serviceId;

    @Column(name = "barbershop_id")
    private UUID barbershopId;
}
