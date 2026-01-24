package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.ApiResponse;
import com.example.BooklyBarbershopBot.dto.BookDatesData;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Обработчик callback-запросов для перехода от выбора услуг к выбору даты записи.
 * <p>
 * Реагирует на префикс {@code continue_services_}. Класс извлекает из временного состояния
 * {@link BookingData} список выбранных пользователем услуг, запрашивает у API Yclients
 * доступные даты для данного набора услуг и мастера, после чего формирует календарную сетку кнопок.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContinueServicesCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;
    private final BookingStateService bookingStateService;

    /**
     * Проверяет, является ли запрос сигналом к переходу к выбору даты.
     *
     * @param data строка данных callback.
     * @return {@code true}, если данные начинаются с "continue_services_".
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("continue_services_");
    }

    /**
     * Обрабатывает логику получения и отображения доступных дат.
     * <p>
     * Процесс обработки:
     * <ol>
     * <li>Восстановление контекста бронирования из {@link BookingStateService}.</li>
     * <li>Запрос дат через {@link YclientsService#getAvailableBookingDates}.</li>
     * <li>Преобразование дат из формата ISO в читаемый вид (dd.MM.yyyy).</li>
     * <li>Построение многострочной клавиатуры (по 3 кнопки в ряд) для выбора конкретного дня.</li>
     * </ol>
     *
     * @param callbackQuery объект запроса от Telegram.
     * @param messageSender сервис для взаимодействия с пользователем.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 4);
            if (parts.length < 4) {
                messageSender.sendMessage(chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            Long staffId = Long.parseLong(parts[2]);
            String slug = parts[3];

            BookingData bookingData = bookingStateService.get(chatId);
            if (bookingData == null || bookingData.getServiceIds() == null || bookingData.getServiceIds().isEmpty()) {
                messageSender.sendMessage(chatId, "⚠️ Не выбраны услуги для записи.");
                return;
            }

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();

                try {
                    // Получаем доступные даты
                    ApiResponse<BookDatesData> response = yclientsService.getAvailableBookingDates(companyId, staffId, bookingData.getServiceIds());
                    List<String> dates = response.getData().getBookingDates();

                    if (dates == null || dates.isEmpty()) {
                        messageSender.sendMessage(chatId, "⏱ Нет доступных дат для записи.");
                        return;
                    }


                    // Формируем кнопки для выбора даты
                    List<List<InlineKeyboardButton>> rows = buildKeyboard(dates, 3, (date, index) -> {
                        String serviceIdsStr = bookingData.getServiceIds()
                                .stream().map(String::valueOf)
                                .collect(Collectors
                                        .joining(","));
                        return InlineKeyboardButton.builder()
                                .text(formatDate(date))
                                .callbackData(
                                        String.format("date_%s_%d_%s_%s", date, staffId, serviceIdsStr, slug))
                                .build();
                    });

                    messageSender.sendMessage(
                            chatId,"📅 Выберите дату для записи:",
                            InlineKeyboardMarkup.builder()
                                    .keyboard(rows)
                                    .build());

                } catch (Exception e) {
                    log.error("Ошибка при получении дат", e);
                    messageSender.sendMessage(chatId, "❌ Не удалось получить доступные даты. Попробуйте позже.");
                }

            }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка при обработке callback continue_services_", e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    /**
     * Вспомогательный метод для форматирования даты в человекочитаемый стандарт.
     * * @param isoDate строка даты в формате "yyyy-MM-dd".
     * @return строка в формате "dd.MM.yyyy".
     */
    private String formatDate(String isoDate) {
        LocalDate date = LocalDate.parse(isoDate); // "2025-07-29"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return date.format(formatter);              // → "29.07.2025"
    }

    /**
     * Универсальный метод для построения многоколоночной клавиатуры.
     * * @param items список исходных данных для кнопок.
     * @param columns количество колонок в одном ряду.
     * @param buttonMapper функция преобразования элемента данных в кнопку {@link InlineKeyboardButton}.
     * @return список рядов кнопок для {@link InlineKeyboardMarkup}.
     */
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