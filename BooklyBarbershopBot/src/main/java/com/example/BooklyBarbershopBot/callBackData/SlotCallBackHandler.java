package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingStateService;
import com.example.BooklyBarbershopBot.service.yclientsService.YclientsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Обработчик окончательного выбора временного слота.
 * <p>
 * Данный класс фиксирует выбранное время записи, агрегирует все накопленные данные
 * (мастер, услуги, дата) и переводит диалог в режим сбора персональных данных клиента.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;
    private final BookingStateService bookingStateService;

    /**
     * Поддерживает обработку конкретных временных слотов.
     * @param data строка формата "slot_yyyy-MM-dd_HH-mm_staffId_slug"
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("slot_");
    }

    /**
     * Выполняет финальную сборку объекта бронирования перед подтверждением.
     * <p>
     * Шаги обработки:
     * 1. Декодирование времени из "безопасного" строкового формата.
     * 2. Валидация и коррекция часового пояса для работы с OffsetDateTime.
     * 3. Получение человекочитаемых названий (мастер, услуги) через API.
     * 4. Сохранение полного контекста в {@link BookingStateService}.
     * 5. Активация состояния {@code awaitingFullName} для приема имени пользователя.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 6);
            if (parts.length < 5) {
                messageSender.sendMessage(chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            String date = parts[1];        // 2025-07-01
            String time = parts[2];        // 17-15
            Long staffId = Long.parseLong(parts[3]);

            String slug = parts.length >= 6 ? parts[5] : "";

            String isoDatetime = restoreIsoDatetime(date + "_" + time); // → "2025-07-01T17:15:00"

            // Исправляем однозначное смещение +3:00 → +03:00
            String offset = "+3:00";
            if (offset.matches("[+-]\\d:.*")) {
                offset = offset.replaceFirst("([+-])(\\d):", "$10$2:");
            }

            OffsetDateTime offsetDateTime = OffsetDateTime.parse(isoDatetime + offset);

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();

                // Получаем BookingData из кеша, он должен уже содержать выбранные услуги
                BookingData bookingData = bookingStateService.get(chatId);
                if (bookingData == null) {
                    bookingData = new BookingData();
                }

                bookingData.setSlug(slug);
                bookingData.setStaffId(staffId);
                bookingData.setDatetime(offsetDateTime);
                bookingData.setStaffName(yclientsService.getStaffName(companyId, staffId));

                if (bookingData.getServiceIds() != null && !bookingData.getServiceIds().isEmpty()) {
                    List<String> serviceNames = bookingData.getServiceIds().stream()
                            .map(serviceId -> {
                                try {
                                    return yclientsService.getServiceName(companyId, serviceId);
                                } catch (Exception e) {
                                    log.warn("Не удалось получить название услуги id={}", serviceId, e);
                                    return "Неизвестная услуга";
                                }
                            })
                            .toList();
                    bookingData.setServiceNames(serviceNames);
                } else {
                    bookingData.setServiceNames(Collections.emptyList());
                }
                bookingStateService.put(chatId, bookingData);

                bookingData.setAwaitingFullName(true);
                bookingStateService.put(chatId, bookingData);


                StringBuilder sb = new StringBuilder();
                sb.append("✅<b>Вы выбрали дату и время:</b>\n")
                        .append("⏰ ").append(formatUserFriendlyDatetime(bookingData.getDatetime())).append("\n")
                        .append("💈<b>Мастер:</b>").append(bookingData.getStaffName()).append("\n");

                if (bookingData.getServiceNames() != null && !bookingData.getServiceNames().isEmpty()) {
                    sb.append("✂️<b>Услуги:</b>")
                            .append(String.join(", ", bookingData.getServiceNames()))
                            .append("\n")
                            .append("• • • • • • • • • • • • • •\n")  // лёгкая разделительная линия
                            .append(" <b>Пожалуйста, введите своё имя</b>."); // один раз
                }

                messageSender.sendMessage(
                        chatId,
                        sb.toString(),
                        null,        // клавиатуры нет
                        "HTML"
                );
            }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка при обработке callback slot_", e);
            messageSender.sendMessage(chatId, "❌ Произошла ошибка при выборе времени.");
        }
    }

    /**
     * Форматирует ISO дату-время в удобочитаемый вид dd/MM/yyyy HH:mm.
     *
     * @param datetime дата-время в формате ISO
     * @return форматированная строка
     */
    public String formatUserFriendlyDatetime(OffsetDateTime datetime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm");
        return datetime.toLocalDateTime().format(formatter); // форматируем в нужный вид
    }

    /**
     * Преобразует внутренний строковый формат даты в стандарт ISO 8601.
     */
    private String restoreIsoDatetime(String safeDatetime) {
        String[] parts = safeDatetime.split("_");
        String date = parts[0]; // "2025-07-01"
        String time = parts[1].replace("-", ":"); // "17:15"
        return date + "T" + time + ":00";
    }

}
