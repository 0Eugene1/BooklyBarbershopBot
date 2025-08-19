package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.handlers.MyBookingsHandler;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelMenuCallBackHandler implements CallBackHandler {

    private final MyBookingsHandler myBookingsHandler;
    private final ClientService clientService;

    @Override
    public boolean supports(String data) {
        return "cancel_menu".equals(data);
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        Long chatId = callbackQuery.getMessage().getChatId();

        if (clientService.findByTelegramId(chatId).isEmpty()) {
            try {
                bot.execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("⚠️ Клиент не найден. Пожалуйста, зарегистрируйтесь.")
                        .build());
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения для chatId={}", chatId, e);
            }
            return;
        }

        myBookingsHandler.handle(chatId, new MyBookingsHandler.TelegramMessageSender() {
            @Override
            public void sendMessage(Long chatId, String text) {
                try {
                    bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
                } catch (Exception e) {
                    log.error("Ошибка при отправке сообщения для chatId={}", chatId, e);
                }
            }

            @Override
            public void executeMessage(SendMessage message) throws TelegramApiException {
                bot.execute(message);
            }
        });

        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Выберите запись для отмены из списка выше.")
                    .build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения для chatId={}", chatId, e);
        }
    }
}
