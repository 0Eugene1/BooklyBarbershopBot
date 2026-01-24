package com.example.BooklyBarbershopBot.sendMessage;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Основная реализация сервиса отправки сообщений.
 * <p>
 * Класс инкапсулирует логику взаимодействия с библиотекой TelegramBots,
 * предоставляя высокоуровневые методы для отправки текста, разметки и клавиатур.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Primary
public class TelegramSenderService implements MessageSender {

    /**
     * Провайдер для ленивой загрузки бина TelegramBot.
     * Помогает избежать циклических зависимостей при инициализации контекста Spring.
     */
    private final ObjectProvider<TelegramBot> telegramBotProvider;

    /**
     * Отправляет простое текстовое сообщение.
     */
    @Override
    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null, null);
    }

    /**
     * Отправляет текстовое сообщение с инлайн-клавиатурой.
     */
    @Override
    public void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        sendMessage(chatId, text, markup, null);
    }

    /**
     * Отправляет сообщение с использованием разметки Markdown.
     * Используется для форматирования текста (жирный, курсив, ссылки).
     *
     * @param chatId идентификатор чата получателя.
     * @param text   текст сообщения.
     */
    @Override
    public void sendMarkdown(Long chatId, String text) {
        sendMessage(chatId, text, null, "Markdown");
    }

    /**
     * Базовый метод для формирования и выполнения запроса на отправку сообщения.
     * <p>
     * Метод оборачивает вызов в блок try-catch для предотвращения падения приложения
     * при ошибках связи с API Telegram (например, если бот заблокирован пользователем).
     *
     * @param chatId    ID чата.
     * @param text      Текст сообщения.
     * @param markup    Инлайн-клавиатура (опционально).
     * @param parseMode Режим парсинга разметки (HTML, Markdown).
     */
    @Override
    public void sendMessage(
            Long chatId,
            String text,
            InlineKeyboardMarkup markup,
            String parseMode
    ) {
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(markup)
                    .build();

            if (parseMode != null) {
                msg.setParseMode(parseMode);
            }

            // Получаем актуальный объект бота и выполняем отправку
            telegramBotProvider.getObject().execute(msg);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения в Telegram. ChatId: {}, Текст: {}, Причина: {}",
                    chatId, text, e.getMessage());
        }
    }
}