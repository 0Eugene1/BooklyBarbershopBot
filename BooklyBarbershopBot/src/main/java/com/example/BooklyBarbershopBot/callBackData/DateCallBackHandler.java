package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookTimeDto;
import com.example.BooklyBarbershopBot.dto.BookTimeResponse;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Обработчик callback-запросов выбора даты для записи.
 * <p>
 * Обрабатывает callbackData, начинающиеся с "date_".
 * Извлекает дату, идентификаторы мастера и услуги, а также slug барбершопа из callbackData.
 * Запрашивает доступное время записи у Yclients API и отправляет пользователю
 * клавиатуру с кнопками выбора конкретного времени.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DateCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;

    /**
     * Проверяет, начинается ли callbackData с "date_".
     *
     * @param data данные callback
     * @return true, если поддерживает обработку выбора даты
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("date_");
    }

    /**
     * Обрабатывает callbackQuery, извлекает параметры даты, мастера, услуги и slug,
     * получает доступные временные слоты через Yclients и отправляет пользователю меню выбора времени.
     *
     * @param callbackQuery объект callbackQuery
     * @param bot           экземпляр TelegramBot
     */
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
            String[] serviceIdsStr = parts[3].split(",");
            List<Long> serviceIds = Arrays.stream(serviceIdsStr)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            String slug = parts[4];

            BookingData bookingData = bot.getBookingCache().get(chatId);
            if (bookingData == null || bookingData.getServiceIds() == null || bookingData.getServiceIds().isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Не выбраны услуги для записи.");
                return;
            }

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();
                try {
                    // Передаем список всех выбранных услуг
                    BookTimeResponse timeResponse =
                            yclientsService.getAvailableTimes(companyId, staffId, date, serviceIds);

                    if (timeResponse.getData() == null || timeResponse.getData().isEmpty()) {
                        sendMessage(bot, chatId, "⏱ Нет свободного времени на эту дату.");
                        return;
                    }

                    List<String> timeStrings = timeResponse.getData().stream()
                            .map(BookTimeDto::getTime)
                            .collect(Collectors.toList());

                    List<List<InlineKeyboardButton>> rows = buildKeyboard(timeStrings, 3, (time, index) -> {
                        BookTimeDto slot = timeResponse.getData().get(index);
                        String safeDatetime = slot.getDatetime().substring(0, 16).replace("T", "_").replace(":", "-");
                        String callbackData = String.format("slot_%s_%d_%d_%s", safeDatetime, staffId, serviceIds.getFirst(), slug);
                        if (callbackData.length() > 64) {
                            callbackData = String.format("slot_%s_%d_%s", safeDatetime, staffId, serviceIds);
                        }
                        return InlineKeyboardButton.builder()
                                .text(time)
                                .callbackData(callbackData)
                                .build();
                    });

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

    /**
     * Вспомогательный метод для отправки сообщений пользователю.
     *
     * @param bot    экземпляр TelegramBot
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }

    private <T> List<List<InlineKeyboardButton>> buildKeyboard(
            List<T> items,
            int columns,
            BiFunction<T, Integer, InlineKeyboardButton> buttonMapper) {

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