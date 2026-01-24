package com.example.BooklyBarbershopBot.sendMessage;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Интерфейс для абстрагирования процесса отправки сообщений в Telegram.
 * <p>
 * Обеспечивает гибкость системы, позволяя заменять реализацию отправки
 * (например, для юнит-тестирования или перехода на асинхронную очередь)
 * без изменения бизнес-логики хендлеров.
 */
public interface MessageSender {

    /**
     * Отправляет простое текстовое сообщение без кнопок и форматирования.
     *
     * @param chatId уникальный идентификатор чата получателя.
     * @param text   содержание сообщения.
     */
    void sendMessage(Long chatId, String text);

    /**
     * Отправляет текстовое сообщение с прикрепленной встроенной клавиатурой.
     *
     * @param chatId   уникальный идентификатор чата получателя.
     * @param text     содержание сообщения.
     * @param keyboard объект {@link InlineKeyboardMarkup} с кнопками.
     */
    void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard);

    /**
     * Отправляет сообщение, размеченное с помощью Markdown.
     * Полезно для выделения текста жирным, курсивом или вставки ссылок.
     *
     * @param chatId уникальный идентификатор чата получателя.
     * @param text   содержание сообщения в формате Markdown.
     */
    void sendMarkdown(Long chatId, String text);

    /**
     * Универсальный метод отправки сообщения с полной кастомизацией.
     *
     * @param chatId    уникальный идентификатор чата получателя.
     * @param text      содержание сообщения.
     * @param markup    объект клавиатуры (может быть null).
     * @param parseMode режим парсинга текста (HTML, Markdown, MarkdownV2).
     */
    void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup, String parseMode);
}