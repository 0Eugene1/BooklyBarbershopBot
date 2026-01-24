package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingStateService;
import com.example.BooklyBarbershopBot.service.ParsingDtoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Обработчик выбора мастера пользователем.
 * <p>
 * Реагирует на callback-запросы с префиксом {@code staff_}.
 * Класс извлекает список услуг, которые оказывает данный мастер в конкретном филиале,
 * и предоставляет пользователю инлайн-клавиатуру для выбора сервиса.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;
    private final BookingStateService bookingStateService;

    /**
     * Проверяет, является ли callback-запрос выбором мастера.
     * * @param data строка формата "staff_{id}_{slug}"
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("staff_");
    }

    /**
     * Обрабатывает выбор мастера и загружает доступные ему услуги.
     * <p>
     * Алгоритм:
     * 1. Парсинг {@code staffId} и идентификатора филиала.
     * 2. Получение всех услуг компании через {@link ParsingDtoService}.
     * 3. Фильтрация услуг: остаются только те, где в списке персонала присутствует данный мастер.
     * 4. Инициализация/обновление состояния бронирования в {@link BookingStateService}.
     * 5. Отправка клавиатуры услуг с указанием их стоимости.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 3);
            if (parts.length < 3) {
                messageSender.sendMessage(chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            String staffIdStr = parts[1];
            String slug = parts[2];

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();
                try {
                    List<ServiceDto> services = parsingDtoService.getServicesParsed(companyId);
                    long staffId = Long.parseLong(staffIdStr);

                    List<ServiceDto> staffServices = services.stream()
                            .filter(s -> {
                                List<StaffDto> staffList = s.getStaff();
                                return staffList != null && staffList.stream().anyMatch(staff -> staff.getId() == staffId);
                            }).toList();

                    if (staffServices.isEmpty()) {
                        messageSender.sendMessage(chatId, "🔍 У этого мастера пока нет доступных услуг.");
                        return;
                    }

                    BookingData bookingData = bookingStateService.getOrCreate(chatId);

                    if (bookingData.getServiceIds() == null) {
                        bookingData.setServiceIds(new ArrayList<>());
                    }

                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    for (ServiceDto service : staffServices) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(service.getTitle() +
                                (service.getPriceMin() != null ? " (от " + service.getPriceMin().intValue() + "₽)" : ""));
                        button.setCallbackData("service_" + service.getId() + "_" + staffIdStr + "_" + slug);
                        rows.add(Collections.singletonList(button));
                    }

                    messageSender.sendMessage(
                            chatId,
                            "💇 Услуги мастера:",
                            InlineKeyboardMarkup.builder()
                                    .keyboard(rows)
                                    .build()
                    );

                } catch (Exception e) {
                    log.error("Ошибка при получении услуг мастера", e);
                    messageSender.sendMessage(chatId, "❌ Не удалось получить услуги мастера. Попробуйте позже.");
                }
            }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка при обработке callback staff_", e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

}
