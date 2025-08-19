package com.example.BooklyBarbershopBot.sendMessage;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public interface TelegramMessageSender {
    void sendMessage(Long chatId, String text);

    void executeMessage(SendMessage message) throws TelegramApiException;
}
