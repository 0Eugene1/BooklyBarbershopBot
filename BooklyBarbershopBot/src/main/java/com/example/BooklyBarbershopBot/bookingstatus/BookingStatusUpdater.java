package com.example.BooklyBarbershopBot.bookingstatus;

import com.example.BooklyBarbershopBot.enums.BookingStatus;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Автоматизированный планировщик для управления жизненным циклом статусов бронирования.
 * <p>
 * Класс инкапсулирует логику фонового мониторинга временных меток записей.
 * Он выполняет массовые обновления статусов напрямую в базе данных, исключая необходимость
 * загрузки объектов в память приложения, что обеспечивает высокую производительность.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingStatusUpdater {

    private final BookingRepository bookingRepository;

    /**
     * Выполняет пакетное обновление статусов записей на основе текущего времени.
     * <p>
     * Работа метода разделена на два этапа:
     * <ol>
     * <li><b>Активация:</b> Перевод записей из {@link BookingStatus#CONFIRMED}
     * в {@link BookingStatus#IN_PROGRESS}, если время начала записи (datetime) наступило.</li>
     * <li><b>Завершение:</b> Перевод записей из текущих активных статусов
     * в {@link BookingStatus#COMPLETED}, если расчетное время окончания (endTime) осталось в прошлом.</li>
     * </ol>
     * Метод помечен {@link Transactional}, чтобы гарантировать атомарность обоих обновлений.
     * Запуск происходит по расписанию каждые 15 минут.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void completeFinishedBookings() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Обновляем записи, которые должны начаться
        int started = bookingRepository.markInProgress(
                BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS,
                now
        );

        // 2. Завершаем записи, время которых истекло
        int completed = bookingRepository.completeFinishedBookings(
                List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
                BookingStatus.COMPLETED,
                now
        );

        if (started > 0 || completed > 0) {
            log.info("Регламентное обновление статусов: переведено в работу = {}, завершено = {}", started, completed);
        } else {
            log.debug("Записей для обновления статусов не обнаружено.");
        }
    }
}