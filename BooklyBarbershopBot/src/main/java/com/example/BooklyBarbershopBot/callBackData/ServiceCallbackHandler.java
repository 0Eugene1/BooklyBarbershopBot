package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.ApiResponse;
import com.example.BooklyBarbershopBot.dto.BookDatesData;
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
public class ServiceCallbackHandler implements CallbackHandler {
    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("service_");
    }


    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 4);
            if (parts.length < 4) {
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            Long serviceId = Long.parseLong(parts[1]);
            Long staffId = Long.parseLong(parts[2]);
            String slug = parts[3];

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();

                try {
                    ApiResponse<BookDatesData> response =
                            yclientsService.getAvailableBookingDates(companyId, staffId, serviceId);

                    log.info("➡️ Запрос /book_dates с параметрами: companyId={}, staffId={}, serviceId={}",
                            companyId, staffId, serviceId);

                    if (response.getData() == null ||
                            response.getData().getBookingDates() == null ||
                            response.getData().getBookingDates().isEmpty()) {
                        sendMessage(bot, chatId, "📅 Нет доступных дат для записи.");
                        return;
                    }

                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    for (String date : response.getData().getBookingDates()) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(date);
                        button.setCallbackData("date_" + date + "_" + staffId + "_" + serviceId + "_" + slug);
                        rows.add(Collections.singletonList(button));
                    }

                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                    keyboard.setKeyboard(rows);

                    SendMessage message = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("📅 Выберите дату:")
                            .replyMarkup(keyboard)
                            .build();

                    bot.execute(message);
                } catch (Exception e) {
                    log.error("Ошибка при получении дат", e);
                    sendMessage(bot, chatId, "❌ Не удалось получить даты бронирования.");
                }
            }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка обработки callback service_", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка.");
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
