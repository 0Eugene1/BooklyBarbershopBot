package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.ParsingDtoService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик callback-запросов для выбора услуги.
 * <p>
 * Поддерживает callbackData, начинающиеся с "service_".
 * Извлекает serviceId, staffId и slug барбершопа из callbackData,
 * запрашивает доступные даты записи через Yclients API и отображает их в виде клавиатуры.
 * Если даты отсутствуют или барбершоп не найден — отправляет соответствующее сообщение.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceCallBackHandler implements CallBackHandler {
    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;


    /**
     * Проверяет, начинается ли callbackData с "service_".
     *
     * @param data данные callback
     * @return true, если поддерживается обработка выбора услуги
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("service_");
    }


    /**
     * Обрабатывает callbackQuery, извлекает идентификаторы услуги, мастера и slug,
     * запрашивает доступные даты для записи и отправляет пользователю клавиатуру с этими датами.
     *
     * @param callbackQuery объект callbackQuery
     * @param bot           экземпляр TelegramBot
     */

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        try {
            String[] parts = data.split("_", 4);
            if (parts.length < 4) {
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            Long serviceId = Long.parseLong(parts[1]);
            Long staffId = Long.parseLong(parts[2]);
            String slug = parts[3];

            // Получаем или создаем BookingData
            BookingData bookingData = bot.getBookingCache().get(chatId);
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
                        sendMessage(bot, chatId, "⚠️ Услуга не найдена.");
                        return;
                    }

                    // Добавляем serviceId, если его еще нет
                    if (!finalBookingData.getServiceIds().contains(serviceId)) {
                        finalBookingData.getServiceIds().add(serviceId);
                        log.info("Добавлен serviceId={}, текущий список serviceIds: {}", serviceId, finalBookingData.getServiceIds());
                    } else {
                        log.info("serviceId={} уже выбран, не добавляем повторно", serviceId);
                        sendMessage(bot, chatId, "⚠️ Эта услуга уже выбрана. К сожалению, на данный момент нет возможности добавлять одинаковые услуги. Выберите другую или продолжите к выбору даты.");
                    }

                    // Сохраняем обратно в кэш
                    bot.getBookingCache().put(chatId, finalBookingData);

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
                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);

                    SendMessage message = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(sb.toString())
                            .replyMarkup(keyboard)
                            .parseMode("HTML")
                            .build();

                    bot.execute(message);

                } catch (Exception e) {
                    log.error("Ошибка при обработке услуги", e);
                    sendMessage(bot, chatId, "❌ Не удалось обработать выбор услуги.");
                }

            }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка в callback service_", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    /**
     * Вспомогательный метод для отправки сообщения пользователю.
     *
     * @param bot    экземпляр TelegramBot
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    private void sendMessage(TelegramBot bot, Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text);
            if (keyboard != null) {
                builder.replyMarkup(keyboard);
            }
            bot.execute(builder.build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }

    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        sendMessage(bot, chatId, text, null);
    }
}
