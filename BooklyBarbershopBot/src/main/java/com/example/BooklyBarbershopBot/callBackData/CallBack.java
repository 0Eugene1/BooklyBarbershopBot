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

    /** Список обработчиков callback-запросов */
    private final List<CallBackHandler> handlers;

    /** Ссылка на основной Telegram-бот для отправки сообщений */
    @Setter
    private TelegramBot telegramBot;

    /**
     * Основной метод обработки callback-запроса.
     * Делегирует обработку первому обработчику, поддерживающему данные.
     * @param callbackQuery входящий callbackQuery от Telegram
     */
    public void handelCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        for (CallBackHandler handler : handlers) {
            if (handler.supports(data)) {
                handler.handle(callbackQuery, telegramBot);
                return;
            }
        }
        sendMessage(callbackQuery.getMessage().getChatId(), "⚠️ Неизвестная команда.");
    }

    /**
     * Вспомогательный метод отправки сообщения пользователю.
     * @param chatId идентификатор чата
     * @param text текст сообщения
     */
    private void sendMessage(Long chatId, String text) {
        try {
            telegramBot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}