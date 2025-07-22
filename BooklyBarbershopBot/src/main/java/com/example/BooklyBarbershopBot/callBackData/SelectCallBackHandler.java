package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Component
@Slf4j
@RequiredArgsConstructor
public class SelectCallBackHandler implements CallBackHandler {
    @Override
    public boolean supports(String data) {
        return data.startsWith("select_service_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 3);
            String serviceId = parts[1];

            if (parts.length < 3) {
                sendMessage(bot, chatId, "Ошибка данных в callBack");
                return;
            }
            String serviceIdStr = parts[2].split("_")[0];
            String slug = parts[2].substring(parts[2].indexOf('_') + 1);

            
        }
    }

        private void sendMessage (TelegramBot bot, Long chatId, String text){
            try {
                bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения", e);
            }
        }

    }
