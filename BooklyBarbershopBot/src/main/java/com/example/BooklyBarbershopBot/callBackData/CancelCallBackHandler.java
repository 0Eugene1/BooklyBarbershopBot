package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.RecordInfo;
import com.example.BooklyBarbershopBot.service.BookingStorageService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCallBackHandler implements CallbackHandler {

    private final YclientsService yclientsService;
    private final BookingStorageService bookingStorage;

    @Override
    public boolean supports(String data) {
        return data.startsWith("cancel_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        Long chatId = callbackQuery.getMessage().getChatId();

        RecordInfo record = bookingStorage.get(chatId);
        if (record == null) {
            sendMessage(bot, chatId, "⚠️ Нет записи, которую можно отменить.");
            return;
        }

        boolean success = yclientsService.cancelBooking(record.getRecordId(), record.getRecordHash());
        if (success) {
            bookingStorage.remove(chatId);
            sendMessage(bot, chatId, "✅ Запись отменена.");
        } else {
            sendMessage(bot, chatId, "❌ Не удалось отменить запись. Попробуйте позже.");
        }
    }

    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}

