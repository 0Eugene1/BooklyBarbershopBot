package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;

    public Booking saveBooking(Client client, BookingData data) {
        return bookingRepository.save(
                Booking.builder()
                        .client(client)
                        .slug(data.getSlug())
                        .datetime(data.getDatetime())
                        .staffName(data.getStaffName())
                        .serviceName(data.getServiceName())
                        .build()
        );
    }
}

