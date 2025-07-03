package com.example.BooklyBarbershopBot.callBackData;

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

    @Override
    public boolean supports(String data) {
        return data.startsWith("cancel_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData(); // "cancel_slug"
        Long chatId = callbackQuery.getMessage().getChatId();
        String slug = data.replace("cancel_", "");

        // Предполагаем, что recordId ранее сохранён в кэше или памяти
        Long recordId = bot.getRecordId(chatId);
        if (recordId == null) {
            sendMessage(bot, chatId, "❗ Не найдена активная запись для отмены.");
            return;
        }

        boolean success = yclientsService.cancelBooking(slug, recordId);
        if (success) {
            bot.clearRecordId(chatId); // Очистим кэш, если используете
            sendMessage(bot, chatId, "❌ Ваша запись успешно отменена.");
        } else {
            sendMessage(bot, chatId, "⚠️ Не удалось отменить запись. Попробуйте позже.");
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
}
