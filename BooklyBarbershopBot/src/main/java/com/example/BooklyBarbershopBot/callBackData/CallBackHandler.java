package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Интерфейс обработчика callback-запросов от Telegram.
 * <p>
 * Реализующие классы определяют, поддерживают ли они конкретный callbackData
 * и содержат логику обработки соответствующего callbackQuery.
 */
public interface CallBackHandler {

    /**
     * Проверяет, поддерживает ли обработчик заданные данные callback.
     *
     * @param data данные callback (callbackData)
     * @return true, если обработчик может обработать данные, иначе false
     */
    boolean supports(String data);

    /**
     * Обрабатывает входящий callbackQuery.
     *
     * @param callbackQuery объект callbackQuery от Telegram
     * @param bot           экземпляр TelegramBot для отправки сообщений и взаимодействия с Telegram API
     */
    void handle(CallbackQuery callbackQuery, TelegramBot bot);
}
