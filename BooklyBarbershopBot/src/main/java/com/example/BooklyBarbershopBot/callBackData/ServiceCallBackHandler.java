package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.dto.ServiceDto;
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
import java.util.List;

/**
 * Обработчик выбора конкретной услуги и управления списком выбранных позиций.
 * <p>
 * Реализует логику "корзины": пользователь может добавить одну или несколько услуг
 * к конкретному мастеру. После выбора услуги состояние сохраняется в {@link BookingStateService},
 * и пользователю предлагается либо расширить список услуг, либо перейти к календарю.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceCallBackHandler implements CallBackHandler {
    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;
    private final BookingStateService bookingStateService;


    /**
     * Проверяет, является ли запрос выбором услуги (префикс "service_").
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("service_");
    }


    /**
     * Обрабатывает выбор услуги, обновляет состояние бронирования и выводит текущий список услуг.
     * <p>
     * Алгоритм:
     * 1. Извлекает параметры из {@code callbackData} (serviceId, staffId, slug).
     * 2. Валидирует состояние: если сменился мастер, корзина очищается.
     * 3. Проверяет наличие услуги в API Yclients.
     * 4. Обновляет {@link BookingData} в кэше.
     * 5. Отправляет инлайн-меню с текущим списком и кнопками навигации.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        try {
            String[] parts = data.split("_", 4);
            if (parts.length < 4) {
                messageSender.sendMessage(chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            Long serviceId = Long.parseLong(parts[1]);
            Long staffId = Long.parseLong(parts[2]);
            String slug = parts[3];

            // Получаем или создаем BookingData
            BookingData bookingData = bookingStateService.get(chatId);
            if (bookingData == null || !staffId.equals(bookingData.getStaffId())) {
                // Если это новый мастер или BookingData отсутствует, создаем новый объект
                bookingData = new BookingData();
                bookingData.setServiceIds(new ArrayList<>()); // Инициализируем пустой список услуг
            }
            bookingData.setSlug(slug);
            bookingData.setStaffId(staffId);

            BookingData finalBookingData = bookingData;
            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();

                try {
                    List<ServiceDto> services = parsingDtoService.getServicesParsed(companyId);
                    ServiceDto selectedService = services.stream()
                            .filter(s -> s.getId().equals(serviceId))
                            .findFirst()
                            .orElse(null);

                    if (selectedService == null) {
                        messageSender.sendMessage(chatId, "⚠️ Услуга не найдена.");
                        return;
                    }

                    // Добавляем serviceId, если его еще нет
                    if (!finalBookingData.getServiceIds().contains(serviceId)) {
                        finalBookingData.getServiceIds().add(serviceId);
                        log.info("Добавлен serviceId={}, текущий список serviceIds: {}", serviceId, finalBookingData.getServiceIds());
                    } else {
                        log.info("serviceId={} уже выбран, не добавляем повторно", serviceId);
                        messageSender.sendMessage(chatId, "⚠️ Эта услуга уже выбрана. К сожалению, на данный момент нет возможности добавлять одинаковые услуги. Выберите другую или продолжите к выбору даты.");
                    }

                    // Сохраняем обратно в кэш
                    bookingStateService.put(chatId,finalBookingData);

                    // Формируем сообщение с выбранными услугами
                    StringBuilder sb = new StringBuilder("<b>Вы выбрали:</b>\n");
                    for (Long id : finalBookingData.getServiceIds()) {
                        services.stream()
                                .filter(s -> s.getId().equals(id))
                                .findFirst()
                                .ifPresent(s -> sb.append("• ").append(s.getTitle()).append("✅").append("\n"));
                    }
                    sb.append("\n<b>Выберите действие:</b>");

                    // Формируем кнопки "Добавить ещё услугу" и "Продолжить к выбору даты"
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    rows.add(List.of(
                            InlineKeyboardButton.builder()
                                    .text("➕ Добавить ещё услугу")
                                    .callbackData("choose_service_" + staffId + "_" + slug)
                                    .build()
                    ));
                    rows.add(List.of(
                            InlineKeyboardButton.builder()
                                    .text("✅ Продолжить к выбору даты")
                                    .callbackData("continue_services_" + staffId + "_" + slug)
                                    .build()
                    ));

                    messageSender.sendMessage(
                            chatId,
                            sb.toString(),
                            InlineKeyboardMarkup.builder().keyboard(rows).build(),
                            "HTML");
                } catch (Exception e) {
                    log.error("Ошибка при обработке услуги", e);
                    messageSender.sendMessage(chatId, "❌ Не удалось обработать выбор услуги.");
                }

            }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка в callback service_", e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }
}
