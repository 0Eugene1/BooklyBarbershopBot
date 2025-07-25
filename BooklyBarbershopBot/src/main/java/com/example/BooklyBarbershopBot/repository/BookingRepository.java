package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью Booking.
 * Предоставляет стандартные CRUD-операции и дополнительные методы для поиска записей по клиенту и статусу.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    /**
     * Получить список всех записей (Booking) для данного клиента.
     *
     * @param client клиент, для которого ищем записи
     * @return список записей клиента
     */

    /**
     * Найти последнюю (по убыванию ID) активную запись (по статусу) для клиента.
     * Используется, например, для отмены текущей записи.
     *
     * @param client клиент, для которого ищем запись
     * @param status статус записи (например, "PENDING", "CONFIRMED")
     * @return опциональный объект Booking, если найден
     */
    //Для отмены записи по chatId нужно найти последнюю или активную запись в БД.
    Optional<Booking> findFirstByClientAndStatusOrderByIdDesc(Client client, String status);

}
