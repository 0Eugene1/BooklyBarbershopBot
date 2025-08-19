package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;

/**
 * Класс-распределитель callback-запросов от Telegram.
 * <p>
 * Получает входящие callbackQuery и делегирует их обработку первому подходящему обработчику
 * из списка {@link CallBackHandler}. Если ни один обработчик не поддерживает данные callback,
 * отправляет пользователю сообщение о неизвестной команде.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallBack {

    /**
     * Список обработчиков callback-запросов
     */
    private final List<CallBackHandler> handlers;

    /**
     * Ссылка на основной Telegram-бот для отправки сообщений
     */
    @Setter
    private TelegramBot telegramBot;

    /**
     * Основной метод обработки callback-запроса.
     * Делегирует обработку первому обработчику, поддерживающему данные.
     *
     * @param callbackQuery входящий callbackQuery от Telegram
     */
    public void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        for (CallBackHandler handler : handlers) {
            if (handler.supports(data)) {
                try {
                    handler.handle(callbackQuery, telegramBot);
                } catch (Exception e) {
                    log.error("Ошибка при обработке callbackData={} для chatId={}", data, chatId, e);
                    try {
                        telegramBot.execute(SendMessage.builder()
                                .chatId(chatId.toString())
                                .text("❌ Произошла ошибка. Попробуйте позже.")
                                .build());
                    } catch (Exception ex) {
                        log.error("Ошибка при отправке сообщения об ошибке для chatId={}", chatId, ex);
                    }
                }
                return;
            }
        }

        log.warn("Не найден обработчик для callbackData={} и chatId={}", data, chatId);
        try {
            telegramBot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Некорректный запрос.")
                    .build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения о некорректном запросе для chatId={}", chatId, e);
        }
    }

}