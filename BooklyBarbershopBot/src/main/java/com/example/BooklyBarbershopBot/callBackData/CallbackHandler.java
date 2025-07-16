package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

public interface CallbackHandler {
    boolean supports(String data);
    void handle(CallbackQuery callbackQuery, TelegramBot bot);
}
