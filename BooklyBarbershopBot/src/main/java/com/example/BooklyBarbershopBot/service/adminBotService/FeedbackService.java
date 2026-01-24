package com.example.BooklyBarbershopBot.service.adminBotService;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Feedback;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import com.example.BooklyBarbershopBot.repository.FeedbackRepository;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для управления обратной связью клиентов.
 * <p>
 * Отвечает за сохранение оценок, обработку причин низкого рейтинга
 * и оперативную доставку уведомлений о негативном опыте администраторам заведений.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final AdminBotSender adminBotSender;
    private final BarbershopService barbershopService;
    private final BookingRepository bookingRepository;

    /**
     * Создает и сохраняет первичный отзыв (рейтинг и комментарий).
     *
     * @param barbershopSlug уникальное имя заведения.
     * @param rating         оценка от 1 до 5.
     * @param review         текстовый отзыв клиента.
     * @param bookingId      ID записи (если отзыв по конкретному визиту).
     * @param chatId         Telegram ID клиента.
     * @return сохраненный объект {@link Feedback}.
     */
    public Feedback saveFeedback(
            String barbershopSlug,
            Integer rating,
            String review,
            Long bookingId,
            Long chatId) {

        Feedback feedback = new Feedback();
        feedback.setRating(rating);
        feedback.setReview(review);
        feedback.setAdminNotified(false);
        feedback.setChatId(chatId);

        Booking booking = (bookingId != null)
                ? bookingRepository.findById(bookingId).orElse(null)
                : null;

        feedback.setBooking(booking);
        feedback.setSlug(booking != null ? booking.getSlug() : barbershopSlug);

        UUID barbershopId = (booking != null)
                ? booking.getBarbershopId()
                : barbershopService.getBySlug(barbershopSlug)
                .map(Barbershop::getId)
                .orElseThrow(() -> new IllegalStateException("Барбершоп не найден: " + barbershopSlug));

        feedback.setBarbershopId(barbershopId);

        return feedbackRepository.save(feedback);
    }

    /**
     * Сохраняет детализированную причину негативной оценки и уведомляет администратора.
     * <p>
     * Выполняется в рамках транзакции. Если оценка <= 3, формируется отчет для Telegram.
     *
     * @param feedback объект отзыва.
     * @param reason   текстовая причина (например, "Долго ждал").
     * @return обновленный объект {@link Feedback}.
     */
    @Transactional
    public Feedback saveLowRatingReasonAndNotifyAdmin(Feedback feedback, String reason) {
        feedback.setLowRatingReason(reason);
        feedback = feedbackRepository.saveAndFlush(feedback);

        if (feedback.getRating() != null && feedback.getRating() <= 3 && !feedback.isAdminNotified()) {
            Long adminChatId = barbershopService.getAdminChatId(feedback.getSlug());

            if (adminChatId == null) {
                log.warn("Админ не задан для {}. Уведомление пропущено.", feedback.getSlug());
                return feedback;
            }

            // Обогащаем данными о клиенте из последней записи
            String clientName = "Клиент не найден";
            String clientPhone = "Телефон не указан";

            Optional<Booking> recentBooking = bookingRepository.findTopByClientTelegramIdAndSlugOrderByDatetimeDesc(
                    feedback.getChatId(),
                    feedback.getSlug()
            );

            if (recentBooking.isPresent()) {
                Booking booking = recentBooking.get();
                if (booking.getClient() != null) {
                    clientName = booking.getClient().getFullName();
                    clientPhone = booking.getClient().getPhone();
                }
            }

            String message = """
                📣 <b>Новый негативный отзыв</b>
                
                🗣 Клиент: %s
                📞 Телефон: %s
                🏷 Барбершоп: %s
                ⭐ Оценка: %d
                ❗ Причина: %s
                """.formatted(clientName, clientPhone, feedback.getSlug(), feedback.getRating(), reason);

            try {
                adminBotSender.sendMessage(adminChatId, message);
                feedback.setAdminNotified(true);
                feedbackRepository.save(feedback);
            } catch (Exception e) {
                log.error("Ошибка уведомления админа: {}", e.getMessage());
            }
        }
        return feedback;
    }

    /**
     * Поиск последнего отзыва для предотвращения дубликатов.
     */
    public Optional<Feedback> getRecentFeedback(Long chatId, UUID barbershopId, Long bookingId) {
        if (bookingId != null) {
            return feedbackRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId);
        } else {
            String slug = barbershopService.getSlugByBarbershopId(barbershopId);
            return feedbackRepository.findTopByChatIdAndSlugOrderByCreatedAtDesc(chatId, slug);
        }
    }
}