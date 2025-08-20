package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final BarbershopService barbershopService; // Добавляем зависимость

    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    /**
     * Создаёт Booking из BookingData и Client.
     *
     * @param client клиент, который делает запись
     * @param data   данные записи из кеша
     * @return созданная запись Booking
     */
    public Booking createBookingFromData(Client client, BookingData data) {
        Booking booking = Booking.builder()
                .client(client)
                .slug(data.getSlug())
                .datetime(data.getDatetime())
                .staffId(data.getStaffId())
                .staffName(data.getStaffName())
                .serviceId(data.getServiceIds().isEmpty() ? null : data.getServiceIds().getFirst()) // Сохраняем первый ID для обратной совместимости
                .serviceName(String.join(", ", data.getServiceNames())) // Сохраняем все имена услуг
                .status("PENDING")
                .recordId(data.getRecordId())
                .recordHash(data.getRecordHash())
                .build();

        return bookingRepository.save(booking);
    }

    // В BookingService
    public Optional<Booking> findByRecordIdAndRecordHash(Long recordId, String recordHash) {
        return bookingRepository.findByRecordIdAndRecordHash(recordId, recordHash);
    }

    public List<Booking> findByStatus(String status) {
        return bookingRepository.findByStatus(status);
    }

    public void updateBookingStatus(Booking booking, String status) {
        booking.setStatus(status);
        bookingRepository.save(booking);
    }

    public List<Booking> getAllBookings(Client client) {
        return bookingRepository.findAllByClientOrderByIdDesc(client);
    }

    //cancelBooking для отмены записи с проверкой статуса.
    public void cancelBooking(Long recordId, String recordHash) {
        Optional<Booking> bookingOpt = findByRecordIdAndRecordHash(recordId, recordHash);
        if (bookingOpt.isPresent()) {
            Booking booking = bookingOpt.get();
            if ("PENDING".equals(booking.getStatus()) || "CONFIRMED".equals(booking.getStatus())) {
                updateBookingStatus(booking, "CANCELLED");
                log.info("Booking cancelled: recordId={}, recordHash={}", recordId, recordHash);
            } else {
                log.warn("Cannot cancel booking: recordId={}, recordHash={}, status={}", recordId, recordHash, booking.getStatus());
                throw new IllegalStateException("Booking cannot be cancelled due to its status: " + booking.getStatus());
            }
        } else {
            log.error("Booking not found: recordId={}, recordHash={}", recordId, recordHash);
            throw new IllegalArgumentException("Booking not found for recordId=" + recordId + " and recordHash=" + recordHash);
        }
    }

    //getBookingByRecordId для получения записи по recordId.
    public Optional<Booking> getBookingByRecordId(Long recordId) {
        return bookingRepository.findByRecordIdAndRecordHash(recordId, null)
                .or(() -> bookingRepository.findByRecordIdAndRecordHash(recordId, ""));
    }

    //getBarbershopIdBySlug для получения UUID барбершопа по slug.
    public UUID getBarbershopIdBySlug(String slug) {
        return barbershopService.getBySlug(slug)
                .map(Barbershop::getId)
                .orElse(null);
    }
}