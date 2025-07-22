package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Обработчик callback-запросов для оставления отзывов.
 * <p>
 * Поддерживает callbackData, начинающиеся с "feedback_".
 * Получает slug барбершопа из callbackData, ищет в базе соответствующий барбершоп
 * и отправляет пользователю ссылку для оставления отзыва.
 * Если ссылка на отзывы отсутствует или барбершоп не найден — сообщает об этом пользователю.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;

    /**
     * Проверяет, начинается ли callbackData с "feedback_".
     *
     * @param data данные callback
     * @return true, если поддерживается обработка запроса на отзыв
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("feedback_");
    }

    /**
     * Обрабатывает callbackQuery, извлекает slug барбершопа,
     * отправляет пользователю ссылку для оставления отзыва.
     *
     * @param callbackQuery объект callbackQuery
     * @param bot экземпляр TelegramBot
     */
    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        log.info("Review callback data: {}", callbackQuery.getData());
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        String slug = data.substring("feedback_".length());

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String reviewsUrl = barbershop.getReviewsUrl();

            if (reviewsUrl == null || reviewsUrl.isEmpty()) {
                sendMessage(bot, chatId, "❌ Отзывы для этого барбершопа пока не доступны.");
                return;
            }

            //Отправляем пользователю ссылку на отзывы
            sendMessage(bot, chatId, "💬 Вот ссылка для оставления отзыва:\n" + reviewsUrl);

        }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));
    }

    /**
     * Вспомогательный метод для отправки сообщений пользователю.
     *
     * @param bot экземпляр TelegramBot
     * @param chatId идентификатор чата
     * @param text текст сообщения
     */
    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}