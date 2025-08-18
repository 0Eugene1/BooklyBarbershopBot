package com.example.BooklyBarbershopBot.bookingstatus;

import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingStatusUpdater {

    private final BookingService bookingService;

    // Запуск каждые 30 минут
    @Scheduled(cron = "0 */30 * * * *")
    public void updateCompletedBookings() {
        log.info("Запуск задачи обновления статуса записей");
        List<Booking> confirmedBookings = bookingService.findByStatus("CONFIRMED");
        OffsetDateTime now = OffsetDateTime.now();

        for (Booking booking : confirmedBookings) {
            if (booking.getDatetime() != null && booking.getDatetime().isBefore(now)) {
                log.info("Обновление статуса записи ID={} на COMPLETED", booking.getId());
                bookingService.updateBookingStatus(booking, "COMPLETED");
            }
        }
        log.info("Задача обновления статуса записей завершена");
    }
}
