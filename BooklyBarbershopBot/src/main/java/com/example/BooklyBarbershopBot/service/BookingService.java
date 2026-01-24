package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.enums.BookingStatus;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для управления жизненным циклом записей (бронирований) в локальной базе данных.
 * <p>
 * Обеспечивает сохранение подтвержденных записей, расчет времени окончания сеанса
 * и предоставление истории посещений для клиентов.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BarbershopService barbershopService;

    /**
     * Прямое сохранение сущности бронирования.
     *
     * @param booking объект бронирования.
     * @return сохраненный объект с присвоенным ID.
     */
    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    /**
     * Преобразует временные данные сессии бронирования в постоянную запись в БД.
     * <p>
     * Вычисляет время окончания услуги на основе длительности и устанавливает
     * статус CONFIRMED.
     *
     * @param client клиент, владелец записи.
     * @param data   накопленные данные о выбранном мастере, услугах и времени.
     * @return созданная и сохраненная сущность {@link Booking}.
     * @throws IllegalStateException если филиал (barbershop) не найден по слагу.
     */
    @Transactional
    public Booking createBookingFromData(Client client, BookingData data) {

        UUID barbershopId = barbershopService.getBySlug(data.getSlug())
                .map(Barbershop::getId)
                .orElseThrow(() ->
                        new IllegalStateException("Филиал не найден по слагу: " + data.getSlug()));

        // Определение длительности с механизмом fallback
        Integer durationFromData = data.getTotalDurationMinutes();
        int totalDurationMinutes;

        if (durationFromData == null) {
            log.warn("Длительность не определена для слага {}. Установлено значение по умолчанию: 30 мин.", data.getSlug());
            totalDurationMinutes = 30;
        } else {
            totalDurationMinutes = durationFromData;
        }

        OffsetDateTime startTime = data.getDatetime();
        OffsetDateTime endTime = startTime.plusMinutes(totalDurationMinutes);

        // Сборка объекта бронирования
        Booking booking = Booking.builder()
                .client(client)
                .slug(data.getSlug())
                .datetime(startTime)
                .endTime(endTime)
                .totalDurationMinutes(totalDurationMinutes)
                .staffId(data.getStaffId())
                .staffName(data.getStaffName())
                // Берем первую услугу как основную для поиска, но сохраняем все названия через запятую
                .serviceId(data.getServiceIds().isEmpty() ? null : data.getServiceIds().getFirst())
                .serviceName(String.join(", ", data.getServiceNames()))
                .status(BookingStatus.CONFIRMED)
                .recordId(data.getRecordId())
                .recordHash(data.getRecordHash())
                .barbershopId(barbershopId)
                .build();

        log.info("Локальная запись создана: клиент={}, мастер={}, время={}",
                client.getFullName(), data.getStaffName(), startTime);

        return bookingRepository.save(booking);
    }

    /**
     * Поиск записи по идентификаторам внешней системы Yclients.
     */
    public Optional<Booking> findByRecordIdAndRecordHash(Long recordId, String recordHash) {
        return bookingRepository.findByRecordIdAndRecordHash(recordId, recordHash);
    }

    /**
     * Возвращает полный список записей клиента, отсортированный от новых к старым.
     */
    public List<Booking> getAllBookings(Client client) {
        return bookingRepository.findAllByClientOrderByIdDesc(client);
    }
}