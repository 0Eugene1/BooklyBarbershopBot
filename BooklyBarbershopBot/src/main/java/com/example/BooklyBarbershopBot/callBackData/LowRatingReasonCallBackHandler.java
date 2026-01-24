package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.entity.Feedback;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.adminBotService.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик уточнения причин низкой оценки (Feedback).
 * <p>
 * Реагирует на callback-запросы с префиксом {@code low_rating_reason_}.
 * Позволяет пользователю выбрать готовую причину из списка или отправить
 * развернутый текстовый комментарий при выборе опции "Другое".
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LowRatingReasonCallBackHandler implements CallBackHandler {

    private final FeedbackService feedbackService;
    private final BarbershopService barbershopService;

    /**
     * Хранилище состояний для пользователей, выбравших вариант "Другое".
     * Ключ — ID чата, значение — объект {@link PendingReason} с контекстом записи.
     */
    private final Map<Long, PendingReason> awaitingReason = new ConcurrentHashMap<>();

    /**
     * Проверяет, относится ли данный callback к уточнению причин негативного отзыва.
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("low_rating_reason_");
    }

    /**
     * Обрабатывает выбор предустановленной причины или инициирует режим ожидания текста.
     * <p>
     * Если выбран вариант "other", пользователь заносится в карту {@code awaitingReason},
     * и бот ожидает следующее текстовое сообщение. В остальных случаях причина
     * сохраняется немедленно с уведомлением администратора.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        log.info("Получен callback для низкой оценки: {}", data);  // ← добавь лог для отладки

        String[] parts = data.split("_");
        if (parts.length < 5) {  // Минимум 5 для базового формата
            messageSender.sendMessage(chatId, "❌ Ошибка формата причины.");
            return;
        }


        String slug = parts[3];
        String reasonType = parts[parts.length - 1];  // ← КЛЮЧЕВОЕ ИЗМЕНЕНИЕ: последняя часть = причина (quality/waiting/time/other)

        log.info("Slug: {}, ReasonType: {}", slug, reasonType);  // ← увидишь в логе правильные значения

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            UUID barbershopId = barbershop.getId();

            Feedback feedback = feedbackService
                    .getRecentFeedback(chatId, barbershopId, null)  // ← передай chatId, если добавишь поле (см. ниже)
                    .orElse(null);

            if (feedback == null) {
                messageSender.sendMessage(chatId, "❌ Не удалось найти вашу оценку.");
                return;
            }

            if ("other".equals(reasonType)) {
                awaitingReason.put(chatId, new PendingReason(slug, feedback.getBooking() != null ? feedback.getBooking().getId() : null));
                messageSender.sendMessage(chatId, "Пожалуйста, опишите причину низкой оценки:");
            } else {
                String reasonText = getReasonText(reasonType);
                messageSender.sendMessage(chatId, "✅ Спасибо за обратную связь! Мы обязательно учтём ваше мнение.");
                feedbackService.saveLowRatingReasonAndNotifyAdmin(feedback, reasonText);
            }
        }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));
    }

    private String getReasonText(String reasonType) {
        return switch (reasonType) {
            case "quality" -> "Плохое качество обслуживания";
            case "waiting" -> "Долгое ожидание";
            case "time" -> "Плохая стрижка";
            default -> "Другое";
        };
    }


    /**
     * Контекст ожидания текстового отзыва.
     * @param slug идентификатор заведения.
     * @param bookingId идентификатор бронирования (может быть null).
     */
    private record PendingReason(String slug, Long bookingId) {}

    /**
     * Обрабатывает свободный текст от пользователя, если он находится в режиме ожидания отзыва.
     * * @param chatId идентификатор чата.
     * @param text текст отзыва.
     * @param messageSender сервис отправки уведомлений.
     * @return {@code true}, если сообщение было перехвачено как причина оценки, иначе {@code false}.
     */
    public boolean handleTextReason(Long chatId, String text, MessageSender messageSender) {
        // 1. Проверяем, ждем ли мы вообще текст от этого пользователя
        PendingReason pending = awaitingReason.get(chatId);
        if (pending == null) {
            return false;
        }

        // 2. Проверяем, что пришел именно текст, а не стикер или фото
        if (text == null || text.isBlank()) {
            messageSender.sendMessage(chatId, "⚠️ Пожалуйста, опишите причину текстом, чтобы мы могли стать лучше.");
            return true; // Возвращаем true, так как мы всё еще обрабатываем этот этап (состояние не сброшено)
        }

        // 3. Если текст валиден — удаляем пользователя из режима ожидания и сохраняем
        awaitingReason.remove(chatId);

        barbershopService.getBySlug(pending.slug()).ifPresentOrElse(barbershop -> {
            UUID barbershopId = barbershop.getId();
            Feedback feedback = feedbackService
                    .getRecentFeedback(chatId, barbershopId, pending.bookingId())
                    .orElse(null);

            if (feedback == null) {
                messageSender.sendMessage(chatId, "❌ Ошибка: не удалось найти вашу оценку для сохранения комментария.");
                return;
            }

            feedbackService.saveLowRatingReasonAndNotifyAdmin(feedback, text);
            messageSender.sendMessage(chatId, "✅ Спасибо за ваш отзыв! Мы передали информацию руководству.");

        }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));

        return true;
    }

}
