package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик цепочки сбора обратной связи (Feedback loop).
 * <p>
 * Данный класс реализует многошаговый процесс:
 * 1. Инициация отзыва (feedback_).
 * 2. Обработка численной оценки звездами (rating_).
 * 3. Обработка уточняющих причин при низком рейтинге (low_reason_).
 * <p>
 * Включает логику уведомления администратора о негативных инцидентах.
 */
@Component
@Slf4j
public class ReviewCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;

    public ReviewCallBackHandler(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    /**
     * Поддерживает три типа префиксов, обеспечивая полный цикл сбора фидбэка.
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("feedback_") || data.startsWith("rating_") || data.startsWith("low_reason_");
    }

    /**
     * Маршрутизирует запрос в зависимости от текущего шага опроса.
     * <p>
     * - При {@code feedback_}: вызывает меню выбора звезд.
     * - При {@code rating_}: проверяет порог удовлетворенности. Если оценка < 4,
     * переводит на сбор причин. Если >= 4, выдает внешнюю ссылку.
     * - При {@code low_reason_}: логирует причину и уведомляет администратора заведения.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        if (data.startsWith("feedback_")) {
            // Шаг 1: Пользователь нажал "Оставить отзыв" → показываем рейтинг
            String slug = data.substring("feedback_".length());
            showRatingButtons(messageSender, chatId, slug);

        } else if (data.startsWith("rating_")) {
            // Шаг 2: Пользователь выбрал рейтинг
            String[] parts = data.split("_", 3);
            int rating = Integer.parseInt(parts[1]);
            String slug = parts[2];

            barbershopService.getBySlug(slug).ifPresent(barbershop -> {
                if (rating >= 4) {
                    // Высокий рейтинг → ссылка на отзыв
                    String reviewsUrl = barbershop.getReviewsUrl();
                    messageSender.sendMessage(chatId, "💬 Спасибо за оценку! Вот ссылка для оставления отзыва:\n" + reviewsUrl);
                } else {
                    // Низкий рейтинг → выбираем причину
                    showLowRatingReasons(messageSender, chatId, slug);
                }
            });

        } else if (data.startsWith("low_reason_")) {
            // Шаг 3: Пользователь выбрал причину низкого рейтинга
            String[] parts = data.split("_", 3); // low_reason_<reason>_<slug>
            String reason = parts[1];
            String slug = parts[2];

            barbershopService.getBySlug(slug).ifPresent(barbershop -> {
                Long adminChatId = barbershop.getAdminChatId();
                if (adminChatId != null) {
                    messageSender.sendMessage(adminChatId, "⚠️ Плохой отзыв для "
                            + barbershop.getName() + ": " + reason);
                }
            });

            // Благодарим пользователя
            messageSender.sendMessage(chatId, "Спасибо за ваш фидбэк! Мы обязательно улучшимся. 🙏");
        }
    }

    /**
     * Генерирует вертикальную клавиатуру с кнопками от 1 до 5 звезд.
     */
    private void showRatingButtons(MessageSender messageSender, Long chatId, String slug) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            StringBuilder stars = new StringBuilder();
            for (int j = 0; j < i; j++) {
                stars.append("⭐");
            }

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                    .text(stars.toString())
                    .callbackData("rating_" + i + "_" + slug)
                    .build());

            rows.add(row); // Каждая кнопка — отдельная строка
        }

        markup.setKeyboard(rows);
        messageSender.sendMessage(chatId, "Пожалуйста, оцените барбершоп:", markup);
    }

    /**
     * Отображает кнопки с предустановленными причинами недовольства.
     */
    private void showLowRatingReasons(MessageSender messageSender, Long chatId, String slug) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String[] reasons = {"Долго ждал", "Не понравился сервис", "Другое"};
        for (String reason : reasons) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                    .text(reason)
                    .callbackData("low_reason_" + reason.replace(" ", "") + "_" + slug)
                    .build());
            rows.add(row);
        }

        markup.setKeyboard(rows);
        messageSender.sendMessage(chatId, "😔 Нам жаль, что вы не довольны. Пожалуйста, выберите причину:", markup);
    }

}