package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Slf4j
@Component
public class CallBack {

    private TelegramBot telegramBot;

    public void setTelegramBot(TelegramBot bot) {
        this.telegramBot = bot;
    }

    public void handelCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        log.info("Chat ID: {}", chatId);
        if (chatId < 0) {
            log.warn("Попытка отправки в группу или канал. chatId: {}", chatId);
            return;
        }
        String responseText;

        if (data.startsWith("book_")) {
            responseText = "🛠 Раздел записи пока в разработке.";
        } else if (data.startsWith("about_")) {
            responseText = "🛠 Раздел 'О нас' пока в разработке.";
        } else if (data.startsWith("feedback_")) {
            responseText = "🛠 Раздел отзывов пока в разработке.";
        } else {
            responseText = "⚠️ Неизвестная команда.";
        }

        try {
            telegramBot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(responseText)
                    .build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}
