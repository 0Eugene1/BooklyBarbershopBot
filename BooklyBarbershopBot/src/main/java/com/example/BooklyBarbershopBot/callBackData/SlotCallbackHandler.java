package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotCallbackHandler implements CallbackHandler{

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("slot_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 6);
            if (parts.length < 5) {
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            String date = parts[1];        // 2025-07-01
            String time = parts[2];        // 17-15
            Long staffId = Long.parseLong(parts[3]);
            Long serviceId = Long.parseLong(parts[4]);
            String slug = parts.length >= 6 ? parts[5] : "";

            String datetime = restoreIsoDatetime(date + "_" + time); // → "2025-07-01T17:15:00"

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();

                String staffName = yclientsService.getStaffName(companyId, staffId);
                String serviceName = yclientsService.getServiceName(companyId, serviceId);

                bot.getBookingCache().put(chatId, new BookingData(slug, serviceId, staffId, datetime, staffName, serviceName));

                KeyboardButton contactButton = new KeyboardButton("📱 Отправить номер");
                contactButton.setRequestContact(true);

                KeyboardRow row = new KeyboardRow();
                row.add(contactButton);

                ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
                markup.setResizeKeyboard(true);
                markup.setOneTimeKeyboard(true);

                String confirmText = """
                        ✅ Вы выбрали:
                        • Дата и время: %s
                        • Мастер: %s
                        • Услуга: %s

                        Пожалуйста, отправьте номер телефона для подтверждения записи.
                        """.formatted(datetime, staffName, serviceName);

                SendMessage message = SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(confirmText)
                        .replyMarkup(markup)
                        .build();

                try {
                    bot.execute(message);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка при обработке callback slot_", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка при выборе времени.");
        }
    }

    private String restoreIsoDatetime(String safeDatetime) {
        String[] parts = safeDatetime.split("_");
        String date = parts[0]; // "2025-07-01"
        String time = parts[1].replace("-", ":"); // "17:15"
        return date + "T" + time + ":00";
    }

    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}
