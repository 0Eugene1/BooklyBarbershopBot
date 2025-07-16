package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByClient(Client client);

    //Для отмены записи по chatId нужно найти последнюю или активную запись в БД.
    Optional<Booking> findFirstByClientAndStatusOrderByIdDesc(Client client, String status);

}
