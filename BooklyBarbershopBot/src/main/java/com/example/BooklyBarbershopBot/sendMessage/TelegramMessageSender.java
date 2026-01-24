package com.example.BooklyBarbershopBot.sendMessage;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Специализированный интерфейс для отправки сообщений через Telegram API.
 * <p>
 * В отличие от общего {@link MessageSender}, данный интерфейс предоставляет
 * возможность выполнять предварительно сконфигурированные объекты {@link SendMessage},
 * что дает полный контроль над параметрами сообщения (кнопки, парсинг, предпросмотр ссылок).
 */
public interface TelegramMessageSender {

    /**
     * Быстрая отправка простого текстового сообщения.
     *
     * @param chatId уникальный идентификатор чата.
     * @param text   текст сообщения.
     */
    void sendMessage(Long chatId, String text);

    /**
     * Выполнение подготовленного объекта SendMessage.
     * <p>
     * Позволяет отправлять сообщения со сложной структурой, включая
     * Inline-клавиатуры и специфические режимы разметки.
     *
     * @param message полностью сформированный объект сообщения.
     * @throws TelegramApiException если отправка не удалась (например, пользователь заблокировал бота).
     */
    void executeMessage(SendMessage message) throws TelegramApiException;
}