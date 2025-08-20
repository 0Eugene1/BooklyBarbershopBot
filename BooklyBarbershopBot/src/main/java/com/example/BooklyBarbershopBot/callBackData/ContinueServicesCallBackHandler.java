package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.ApiResponse;
import com.example.BooklyBarbershopBot.dto.BookDatesData;
import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.yclientsService.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContinueServicesCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("continue_services_");
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

            Long staffId = Long.parseLong(parts[2]);
            String slug = parts[3];

            // Получаем BookingData из кэша
            BookingData bookingData = bot.getBookingCache().get(chatId);
            if (bookingData == null || bookingData.getServiceIds() == null || bookingData.getServiceIds().isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Не выбраны услуги для записи.");
                return;
            }

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();

                try {
                    // Получаем доступные даты
                    ApiResponse<BookDatesData> response = yclientsService.getAvailableBookingDates(companyId, staffId, bookingData.getServiceIds());
                    List<String> dates = response.getData().getBookingDates();

                    if (dates == null || dates.isEmpty()) {
                        sendMessage(bot, chatId, "⏱ Нет доступных дат для записи.");
                        return;
                    }


                    // Формируем кнопки для выбора даты
                    List<List<InlineKeyboardButton>> rows = buildKeyboard(dates, 3, (date, index) -> {
                        String serviceIdsStr = bookingData.getServiceIds().stream().map(String::valueOf).collect(Collectors.joining(","));
                        return InlineKeyboardButton.builder().text(formatDate(date)).callbackData(String.format("date_%s_%d_%s_%s", date, staffId, serviceIdsStr, slug)).build();
                    });

                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);

                    SendMessage message = SendMessage.builder().chatId(chatId.toString()).text("📅 Выберите дату для записи:").replyMarkup(keyboard).build();

                    bot.execute(message);

                } catch (Exception e) {
                    log.error("Ошибка при получении дат", e);
                    sendMessage(bot, chatId, "❌ Не удалось получить доступные даты. Попробуйте позже.");
                }

            }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка при обработке callback continue_services_", e);
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

    /**
     * Форматирует дату из ISO формата (yyyy-MM-dd) в dd.MM.yyyy.
     *
     * @param isoDate дата в формате ISO
     * @return отформатированная дата
     */
    private String formatDate(String isoDate) {
        LocalDate date = LocalDate.parse(isoDate); // "2025-07-29"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return date.format(formatter);              // → "29.07.2025"
    }

    private List<List<InlineKeyboardButton>> buildKeyboard(List<String> items, int columns, BiFunction<String, Integer, InlineKeyboardButton> buttonMapper) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            currentRow.add(buttonMapper.apply(items.get(i), i));

            if ((i + 1) % columns == 0 || i == items.size() - 1) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }

        return rows;
    }
}