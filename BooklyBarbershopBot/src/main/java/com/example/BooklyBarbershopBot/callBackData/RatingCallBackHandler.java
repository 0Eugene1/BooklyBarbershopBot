package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.adminBotService.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик первичного получения оценки качества услуг.
 * <p>
 * Реагирует на callback-запросы с префиксом {@code rating_}.
 * Класс выполняет две основные функции:
 * <ol>
 * <li>Сохранение численной оценки в базу данных через {@link FeedbackService}.</li>
 * <li>Сегментация пользователей: лояльным (4-5 звезд) предлагается оставить публичный отзыв,
 * недовольным (1-3 звезды) предлагается уточнить причину жалобы.</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RatingCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final FeedbackService feedbackService;

    /**
     * Проверяет, является ли callback ответом на запрос оценки.
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("rating_");
    }

    /**
     * Обрабатывает выбор количества звезд пользователем.
     * <p>
     * Парсит данные формата {@code rating_<rating>_<slug>_<bookingId>}.
     * Использует лимит сплита для поддержки слагов с нижним подчеркиванием.
     * * @param callbackQuery объект нажатия кнопки.
     * @param messageSender сервис для отправки ответных инструкций.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        // Разбор callbackData: rating_<rating>_<slug>_<bookingId>
        // Используем split с лимитом, чтобы slug мог содержать подчёркивания
        String[] parts = data.split("_", 4);
        if (parts.length < 3) {
            messageSender.sendMessage(chatId, "❌ Ошибка обработки рейтинга.");
            return;
        }

        try {
            Integer rating = Integer.parseInt(parts[1]);
            String slug = parts[2];
            Long bookingId = parts.length > 3 ? parseLongOrNull(parts[3]) : null;

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                feedbackService.saveFeedback(slug, rating, null, bookingId, chatId);

                if (rating >= 4) {
                    // Высокая оценка — отправляем ссылку для отзыва
                    sendReviewLink(messageSender, chatId, barbershop.getReviewsUrl());
                } else {
                    // Низкая оценка — спрашиваем причину
                    sendLowRatingRequest(messageSender, chatId, slug, bookingId);
                }

            }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));

        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга рейтинга: {}", data, e);
            messageSender.sendMessage(chatId, "❌ Ошибка обработки рейтинга.");
        }
    }

    private Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Инициирует сценарий сбора претензий при низкой оценке.
     * Формирует клавиатуру с типовыми проблемами (качество, ожидание и т.д.).
     */
    private void sendLowRatingRequest(MessageSender messageSender, Long chatId, String slug, Long bookingId) {
        String message = "😔 Мы сожалеем, что не оправдали ваших ожиданий.\n\n" +
                "Пожалуйста, укажите причину низкой оценки.";

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
                .text("Плохое обслуживание")
                .callbackData("low_rating_reason_" + slug + "_" + safeBookingId(bookingId) + "_quality")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("Долгое ожидание")
                .callbackData("low_rating_reason_" + slug + "_" + safeBookingId(bookingId) + "_waiting")
                .build());

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
                .text("Плохая стрижка")
                .callbackData("low_rating_reason_" + slug + "_" + safeBookingId(bookingId) + "_time")
                .build());
        row2.add(InlineKeyboardButton.builder()
                .text("Написать своë")
                .callbackData("low_rating_reason_" + slug + "_" + safeBookingId(bookingId) + "_other")
                .build());

        rows.add(row1);
        rows.add(row2);

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(rows).build();

        messageSender.sendMessage(chatId, message, markup);
    }

    private String safeBookingId(Long bookingId) {
        return bookingId != null ? bookingId.toString() : "";
    }

    /**
     * Благодарит за высокую оценку и предоставляет внешнюю ссылку (например, на Google Maps или 2GIS)
     * для повышения публичного рейтинга заведения.
     */
    private void sendReviewLink(MessageSender messageSender, Long chatId, String reviewsUrl) {
        String message = "⭐ Спасибо за вашу оценку!\n\n" +
                "Если хотите, оставьте отзыв о нашем барбершопе: " + reviewsUrl;
        messageSender.sendMessage(chatId, message);
    }

}
