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
     * Найти последнюю (по убыванию ID) активную запись (по статусу) для клиента.
     * Используется, например, для отмены текущей записи.
     *
     * @param client клиент, для которого ищем запись
     * @param statuses статус записи (например, "PENDING", "CONFIRMED")
     * @return опциональный объект Booking, если найден
     */
    //Для отмены записи по chatId нужно найти последнюю или активную запись в БД.
    Optional<Booking> findFirstByClientAndStatusInOrderByIdDesc(Client client, List<String> statuses);

    List<Booking> findAllByClientOrderByIdDesc(Client client);

    Optional<Booking> findByRecordIdAndRecordHash(Long recordId, String recordHash);

    List<Booking> findByStatus(String status);

}
