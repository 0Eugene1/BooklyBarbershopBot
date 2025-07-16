package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;

    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    public Booking saveBooking(Client client, BookingData data) {
        Booking booking = Booking.builder()
                .client(client)
                .slug(data.getSlug())
                .serviceId(data.getServiceId())
                .staffId(data.getStaffId())
                .datetime(data.getDatetime())
                .staffName(data.getStaffName())
                .serviceName(data.getServiceName())
                .recordId(data.getRecordId())
                .recordHash(data.getRecordHash())
                .status("PENDING")
                .build();

        return bookingRepository.save(booking);
    }

    public Optional<Booking> getActiveBooking(Client client) {
        return bookingRepository.findFirstByClientAndStatusOrderByIdDesc(client, "PENDING");
    }

    public void updateBookingStatus(Booking booking, String status) {
        booking.setStatus(status);
        bookingRepository.save(booking);
    }

    public void deleteBooking(Booking booking) {
        bookingRepository.delete(booking);
    }
}


