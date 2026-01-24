package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookTimeDto;
import com.example.BooklyBarbershopBot.dto.BookTimeResponse;
import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingStateService;
import com.example.BooklyBarbershopBot.service.yclientsService.YclientsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Обработчик callback-запросов выбора даты для формирования списка доступного времени.
 * <p>
 * Класс отвечает за переход от выбранного дня к конкретному временному слоту.
 * Он извлекает контекст записи (мастер, услуги, филиал), запрашивает у Yclients API
 * доступные интервалы для этой комбинации и отрисовывает сетку времени.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DateCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;
    private final BookingStateService bookingStateService;

    /**
     * Проверяет, является ли callback-запрос выбором даты.
     *
     * @param data строка callbackData, ожидается префикс {@code date_}.
     * @return {@code true}, если обработчик поддерживает данные.
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("date_");
    }

    /**
     * Обрабатывает выбор даты пользователем.
     * <p>
     * Особенности реализации:
     * <ol>
     * <li>Десериализация списка ID услуг из строки callback.</li>
     * <li>Запрос временных слотов через {@link YclientsService#getAvailableTimes}.</li>
     * <li>Трансформация ISO-даты в безопасный формат для callbackData (замена ":" на "-" и "T" на "_"),
     * чтобы избежать конфликтов при парсинге и уложиться в лимиты Telegram.</li>
     * <li>Динамическая проверка длины callbackData (64 байта) с механизмом сокращения данных при превышении.</li>
     * </ol>
     *
     * @param callbackQuery объект события нажатия кнопки.
     * @param messageSender компонент для отправки сообщений.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 5);
            if (parts.length < 5) {
                messageSender.sendMessage(chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            String date = parts[1]; // формат: 2025-07-01
            Long staffId = Long.parseLong(parts[2]);
            String[] serviceIdsStr = parts[3].split(",");
            List<Long> serviceIds = Arrays.stream(serviceIdsStr)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            String slug = parts[4];

            BookingData bookingData = bookingStateService.get(chatId);
            if (bookingData == null || bookingData.getServiceIds() == null || bookingData.getServiceIds().isEmpty()) {
                messageSender.sendMessage(chatId, "⚠️ Не выбраны услуги для записи.");
                return;
            }

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();
                try {
                    // Передаем список всех выбранных услуг
                    BookTimeResponse timeResponse =
                            yclientsService.getAvailableTimes(companyId, staffId, date, serviceIds);

                    if (timeResponse.getData() == null || timeResponse.getData().isEmpty()) {
                        messageSender.sendMessage(chatId, "⏱ Нет свободного времени на эту дату.");
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

                    messageSender.sendMessage(
                            chatId,"⏰ Выберите удобное время:",
                            InlineKeyboardMarkup.builder()
                                    .keyboard(rows)
                                    .build());
                } catch (Exception e) {
                    log.error("Ошибка при получении времени", e);
                    messageSender.sendMessage(chatId, "❌ Не удалось получить доступное время.");
                }
            }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));
        } catch (Exception e) {
            log.error("Ошибка при обработке callback date_", e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    /**
     * Вспомогательный метод для построения клавиатуры из списка элементов.
     * * @param <T> тип исходных данных.
     * @param items список элементов.
     * @param columns количество колонок.
     * @param buttonMapper логика создания кнопки из элемента.
     * @return сформированная сетка кнопок.
     */
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