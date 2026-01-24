package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для управления бронированиями.
 * <p>
 * Сочетает в себе функционал для обслуживания клиентских запросов (история записей)
 * и административных задач (автоматическая смена статусов по времени).
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Автоматически завершает записи, время которых истекло.
     * Используется планировщиком задач для поддержания актуальности данных.
     */
    @Modifying
    @Query("""
        UPDATE Booking b
        SET b.status = :completed
        WHERE b.status IN :statuses
          AND b.endTime < :now
    """)
    int completeFinishedBookings(
            @Param("statuses") List<BookingStatus> statuses,
            @Param("completed") BookingStatus completed,
            @Param("now") OffsetDateTime now
    );

    /**
     * Переводит записи в состояние "Выполняется", когда наступает время визита.
     */
    @Modifying
    @Query("""
        UPDATE Booking b
        SET b.status = :inProgress
        WHERE b.status = :confirmed
          AND b.datetime < :now
    """)
    int markInProgress(
            @Param("confirmed") BookingStatus confirmed,
            @Param("inProgress") BookingStatus inProgress,
            @Param("now") OffsetDateTime now
    );

    /**
     * Возвращает полную историю записей клиента, отсортированную от новых к старым.
     */
    List<Booking> findAllByClientOrderByIdDesc(Client client);

    /**
     * Находит конкретную запись по уникальной связке идентификаторов Yclients.
     * Используется для обработки отмен и синхронизации с CRM.
     */
    Optional<Booking> findByRecordIdAndRecordHash(Long recordId, String recordHash);

    /**
     * Находит самую свежую запись клиента в конкретном барбершопе.
     * Полезно для автоматического предложения оставить отзыв после стрижки.
     */
    Optional<Booking> findTopByClientTelegramIdAndSlugOrderByDatetimeDesc(
            Long telegramId,
            String slug
    );
}