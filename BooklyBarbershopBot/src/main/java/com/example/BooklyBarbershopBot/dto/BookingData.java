package com.example.BooklyBarbershopBot.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingData {

    private String slug;
    private Long staffId;
    private OffsetDateTime datetime;

    private String staffName;
    private List<String> serviceNames = new ArrayList<>();
    private List<Long> serviceIds = new ArrayList<>();

    private Integer totalDurationMinutes;

    private String phone;
    private boolean awaitingCode;

    private Long recordId;
    private String recordHash;

    /** Локальная запись в статусе PENDING до подтверждения в Yclients. */
    private Long pendingBookingId;

    private String fullName;
    private boolean awaitingFullName;

    // Данные отзыва
    private Integer rating;
    private String lowRatingReason;
    private String reviewText;
}
