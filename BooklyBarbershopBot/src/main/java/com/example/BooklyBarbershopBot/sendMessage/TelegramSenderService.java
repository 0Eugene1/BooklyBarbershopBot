package com.example.BooklyBarbershopBot.sendMessage;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Сервис для отправки сообщений через Telegram-бота.
 * Реализует интерфейс MessageSender.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Primary
public class TelegramSenderService implements MessageSender {
    private final TelegramBot telegramBot;

    /**
     * Отправляет текстовое сообщение в указанный чат Telegram.
     *
     * @param chatId идентификатор чата получателя
     * @param text   текст сообщения
     */
    @Override
    public void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder().chatId(chatId.toString()).text(text).build();
        try {
            telegramBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}