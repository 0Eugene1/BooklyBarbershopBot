package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookTimeDto;
import com.example.BooklyBarbershopBot.dto.BookTimeResponse;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DateCallbackHandler implements CallbackHandler {

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("date_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 5);
            if (parts.length < 5) {
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            String date = parts[1]; // формат: 2025-07-01
            Long staffId = Long.parseLong(parts[2]);
            Long serviceId = Long.parseLong(parts[3]);
            String slug = parts[4];

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();
                try {
                    BookTimeResponse timeResponse =
                            yclientsService.getAvailableTimes(companyId, staffId, date, serviceId);

                    if (timeResponse.getData() == null || timeResponse.getData().isEmpty()) {
                        sendMessage(bot, chatId, "⏱ Нет свободного времени на эту дату.");
                        return;
                    }

                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    for (BookTimeDto slot : timeResponse.getData()) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(slot.getTime());

                        String safeDatetime = slot.getDatetime().substring(0, 16) // 2025-07-01T17:15
                                .replace("T", "_")
                                .replace(":", "-"); // → 2025-07-01_17-15

                        String callbackData = String.format("slot_%s_%d_%d_%s", safeDatetime, staffId, serviceId, slug);

                        if (callbackData.length() > 64) {
                            callbackData = String.format("slot_%s_%d_%d", safeDatetime, staffId, serviceId);
                        }

                        button.setCallbackData(callbackData);
                        rows.add(Collections.singletonList(button));
                    }

                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                    keyboard.setKeyboard(rows);

                    SendMessage message = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("⏰ Выберите удобное время:")
                            .replyMarkup(keyboard)
                            .build();

                    bot.execute(message);
                } catch (Exception e) {
                    log.error("Ошибка при получении времени", e);
                    sendMessage(bot, chatId, "❌ Не удалось получить доступное время.");
                }
            }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));
        } catch (Exception e) {
            log.error("Ошибка при обработке callback date_", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
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
