package com.example.BooklyBarbershopBot.sendMessage;

/**
 * Интерфейс для отправки сообщений пользователям.
 */
public interface MessageSender {

    /**
     * Отправить сообщение в чат с указанным chatId.
     *
     * @param chatId идентификатор чата пользователя
     * @param text текст сообщения для отправки
     */
    void sendMessage(Long chatId, String text);
}