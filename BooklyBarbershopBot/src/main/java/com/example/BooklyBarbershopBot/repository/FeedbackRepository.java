package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для управления сущностями обратной связи (Feedback).
 * <p>
 * Обеспечивает доступ к оценкам и отзывам клиентов, позволяя отслеживать
 * качество обслуживания в разрезе конкретных записей (Booking) и филиалов (Slug).
 */
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /**
     * Находит последний оставленный отзыв для конкретной записи на услугу.
     * <p>
     * Используется для проверки наличия отзыва по завершенному визиту,
     * чтобы предотвратить повторное анкетирование пользователя.
     *
     * @param bookingId идентификатор записи в базе данных.
     * @return {@link Optional} с последним найденным отзывом.
     */
    Optional<Feedback> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

    /**
     * Находит самый свежий отзыв пользователя для конкретного филиала барбершопа.
     * <p>
     * Помогает восстановить контекст недовольства клиента, если он обращается
     * в поддержку или если администратор анализирует историю оценок в данном филиале.
     *
     * @param chatId идентификатор чата пользователя в Telegram.
     * @param slug текстовый идентификатор барбершопа.
     * @return {@link Optional} с последним отзывом пользователя в этом заведении.
     */
    Optional<Feedback> findTopByChatIdAndSlugOrderByCreatedAtDesc(Long chatId, String slug);
}