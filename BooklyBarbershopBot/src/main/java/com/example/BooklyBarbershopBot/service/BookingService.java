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
        UUID barbershopId = barbershopService.getBySlug(data.getSlug())
                .map(Barbershop::getId)
                .orElseThrow(() -> new IllegalStateException("Barbershop not found for slug: " + data.getSlug()));
        Booking booking = Booking.builder()
                .client(client)
                .slug(data.getSlug())
                .datetime(data.getDatetime())
                .staffId(data.getStaffId())
                .staffName(data.getStaffName())
                .serviceId(data.getServiceIds().isEmpty() ? null : data.getServiceIds().getFirst())
                .serviceName(String.join(", ", data.getServiceNames()))
                .status("PENDING")
                .recordId(data.getRecordId())
                .recordHash(data.getRecordHash())
                .barbershopId(barbershopId)
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
}