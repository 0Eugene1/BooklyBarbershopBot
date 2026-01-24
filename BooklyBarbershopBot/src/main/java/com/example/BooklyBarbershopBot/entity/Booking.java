package com.example.BooklyBarbershopBot.entity;

import com.example.BooklyBarbershopBot.enums.BookingStatus;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime datetime;

    @Column(nullable = false,
            name = "end_time",
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime endTime;

    @Column(nullable = false)
    private Integer totalDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    private String staffName;
    private String serviceName;
    private String slug;

    @ManyToOne
    private Client client;

    private Long recordId;
    private String recordHash;

    private Long staffId;
    private Long serviceId;

    @Column(name = "barbershop_id")
    private UUID barbershopId;
}
