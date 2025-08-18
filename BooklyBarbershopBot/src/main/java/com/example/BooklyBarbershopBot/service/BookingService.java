package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;

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
                .serviceId(data.getServiceIds().isEmpty() ? null : data.getServiceIds().get(0)) // Сохраняем первый ID для обратной совместимости
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



//    public Booking createBookingFromData(Client client, BookingData data) {
//        // Например, берём только первый serviceId, если их несколько
//        Long serviceId = data.getServiceIds().isEmpty() ? null : data.getServiceIds().get(0);
//        String serviceName = data.getServiceNames().isEmpty() ? null : data.getServiceNames().get(0);
//
//        Booking booking = Booking.builder()
//                .client(client)
//                .slug(data.getSlug())
//                .datetime(data.getDatetime())
//                .staffId(data.getStaffId())
//                .staffName(data.getStaffName())
//                .serviceId(serviceId)
//                .serviceName(serviceName)
//                .status("PENDING")
//                .recordId(data.getRecordId())
//                .recordHash(data.getRecordHash())
//                .build();
//
//        return bookingRepository.save(booking);
//    }

    public Optional<Booking> getActiveBooking(Client client) {
        return bookingRepository.findFirstByClientAndStatusInOrderByIdDesc(client, List.of("PENDING", "CONFIRMED"));
    }

    public void updateBookingStatus(Booking booking, String status) {
        booking.setStatus(status);
        bookingRepository.save(booking);
    }

    public List<Booking> getAllBookings(Client client) {
        return bookingRepository.findAllByClientOrderByIdDesc(client);
    }

}