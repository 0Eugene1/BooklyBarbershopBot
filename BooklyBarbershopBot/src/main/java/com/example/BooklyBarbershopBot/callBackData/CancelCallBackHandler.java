package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.enums.BookingStatus;
import com.example.BooklyBarbershopBot.repository.BookingRepository;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.BookingStateService;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.service.eventService.BotEventService;
import com.example.BooklyBarbershopBot.service.yclientsService.YclientsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Обработчик callback-запросов для отмены существующей записи.
 * <p>
 * Класс реализует комплексную логику отмены: проверяет права владения записью,
 * актуальность времени, статус записи в локальной БД и инициирует удаление через API Yclients.
 * После успешной отмены предлагает пользователю записаться заново.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCallBackHandler implements CallBackHandler {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final YclientsService yclientsService;
    private final BarbershopService barbershopService;
    private final BotEventService boteventService;
    private final BookingStateService bookingStateService;
    private final BookingRepository bookingRepository;

    /**
     * Проверяет, является ли запрос командой на отмену.
     * Формат данных: {@code cancel_{recordId}_{recordHash}}
     *
     * @param data данные callback.
     * @return {@code true}, если формат соответствует ожидаемому.
     */
    @Override
    public boolean supports(String data) {
        if (data == null || !data.startsWith("cancel_")) {
            return false;
        }
        String[] parts = data.split("_", 3);
        return parts.length == 3 && isNumeric(parts[1]);
    }

    private boolean isNumeric(String str) {
        if (str == null) {
            return false;
        }
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Основной метод обработки отмены записи.
     * <p>
     * Шаги обработки:
     * <ol>
     * <li>Валидация клиента и параметров записи (recordId, hash).</li>
     * <li>Проверка "мягких" ограничений: не является ли запись уже завершенной или прошедшей по времени.</li>
     * <li>Вызов внешнего API Yclients для физической отмены.</li>
     * <li>В случае успеха: смена статуса в БД на {@link BookingStatus#CANCELED} и логирование события.</li>
     * <li>В случае ошибки API (например, запись уже подтверждена на месте): перевод в {@link BookingStatus#COMPLETED}.</li>
     * </ol>
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        Optional<Client> optionalClient = clientService.findByTelegramId(chatId);
        if (optionalClient.isEmpty()) {
            messageSender.sendMessage(chatId, "⚠️ Клиент не найден.", null);
            return;
        }

        String[] parts = data.split("_", 3);
        if (parts.length < 3) {
            messageSender.sendMessage(chatId, "⚠️ Ошибка в данных callback.", null);
            log.warn("Некорректный callbackData: {} для chatId={}", data, chatId);
            return;
        }

        String recordIdStr = parts[1];
        String recordHash = parts[2];

        if (recordIdStr == null || recordIdStr.equals("null") || recordHash == null || recordHash.isEmpty()) {
            messageSender.sendMessage(chatId, "⚠️ Ошибка: отсутствует идентификатор записи или хэш.", null);
            log.warn("Недопустимые recordId={} или recordHash={} для chatId={}", recordIdStr, recordHash, chatId);
            return;
        }

        Long recordId;
        try {
            recordId = Long.parseLong(recordIdStr);
        } catch (NumberFormatException e) {
            messageSender.sendMessage(chatId, "⚠️ Ошибка: некорректный идентификатор записи.", null);
            log.warn("Некорректный recordId={} для chatId={}", recordIdStr, chatId);
            return;
        }

        Optional<Booking> optionalBooking = bookingService.findByRecordIdAndRecordHash(recordId, recordHash);
        if (optionalBooking.isEmpty()) {
            messageSender.sendMessage(chatId,"⚠️ Запись не найдена.", null);
            return;
        }

        Booking booking = optionalBooking.get();
        if (!booking.getClient().getTelegramId().equals(chatId)) {
            messageSender.sendMessage(chatId,"⚠️ Эта запись не принадлежит вам.", null);
            return;
        }

        // Форматтер для даты, как в MyBookingsHandler
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm");
        ZoneId zoneId = ZoneId.of("Europe/Moscow"); // Укажите ваш часовой пояс
        String formattedDate = booking.getDatetime()
                .atZoneSameInstant(zoneId)
                .format(outputFormatter);

        // Статус в человекочитаемом виде
        String statusDisplay = switch (booking.getStatus()) {
            case PENDING -> "Ожидает подтверждения";
            case CONFIRMED -> "Подтверждено";
            case IN_PROGRESS -> "В процессе";
            case COMPLETED -> "Выполнено";
            case CANCELED -> "Отменено";
        };

        StringBuilder text = new StringBuilder();
        text.append("💈 *Мастер*: ").append(booking.getStaffName()).append("\n");
        text.append("✂️ *Услуги*: ").append(booking.getServiceName()).append("\n");
        text.append("⏰ *Дата и время*: ").append(formattedDate).append("\n");
        text.append("Статус: ").append(statusDisplay);

        // Проверяем статус и дату
        OffsetDateTime now = OffsetDateTime.now();

        // Проверка на завершенные записи
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            text.append("\nℹ️ <b>Эта запись уже выполнена.</b>");
            messageSender.sendMarkdown(chatId, text.toString());
            return;
        }
        // Если запись уже прошла
        if (booking.getEndTime().isBefore(now)) {
            text.append("\nℹ️ <b>Эта запись уже завершена и не может быть отменена.</b>");
            booking.setStatus(BookingStatus.COMPLETED);  // ставим статус через enum
            bookingRepository.save(booking);                // сохраняем изменения
            messageSender.sendMarkdown(chatId, text.toString());
            return;
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            text.append("\nℹ️ <b>Эта запись уже отменена или не подлежит отмене.</b>");
            messageSender.sendMarkdown(chatId, text.toString());
            return;
        }

        // Получение barbershopId
        UUID barbershopId = booking.getBarbershopId() != null
                ? booking.getBarbershopId()
                : barbershopService.getBySlug(booking.getSlug())
                .map(Barbershop::getId)
                .orElse(null);
        if (barbershopId == null) {
            log.error("Barbershop not found for slug: {}, recordId: {}, chatId: {}", booking.getSlug(), recordId, chatId);
            messageSender.sendMessage(chatId,"❌ Ошибка: барбершоп не найден.", null);
            return;
        }
        // Пытаемся отменить запись
        // Пытаемся отменить запись через Yclients
        try {
            boolean success = yclientsService.cancelBooking(recordId, recordHash);
            if (success) {
                booking.setStatus(BookingStatus.CANCELED);
                bookingRepository.save(booking);
                bookingStateService.remove(chatId);

                //Сохранение события
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("recordId", recordId);
                eventData.put("recordHash", recordHash);

                boteventService.saveEvent(chatId, barbershopId, "BOOKING_CANCELLED", eventData);

                messageSender.sendMessage(chatId, "✅Запись отменена.", null);
                // Предлагаем выбрать новую услугу
                sendStaffSelectionMenu(messageSender, chatId, booking.getSlug());
            } else {
                // запись не удалась, ставим COMPLETED
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);

                text.append("\nℹ️ <b>Эта запись уже выполнена и не может быть отменена.</b>");
                messageSender.sendMessage(chatId, text.toString(), null);
                log.error("Не удалось отменить запись recordId={} для chatId={}", recordId, chatId);
            }
        } catch (Exception e) {
            log.error("Ошибка при вызове Yclients API для отмены записи recordId={} для chatId={}: {}", recordId, chatId, e.getMessage());
            if (e.getMessage().contains("403") || e.getMessage().contains("Запись подтверждена в филиале")) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);

                text.append("\nℹ️ <b>Эта запись уже выполнена и не может быть отменена.</b>");
                messageSender.sendMessage(chatId, text.toString(), null);
            } else {
                messageSender.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.", null);
            }
        }
    }

    /**
     * Формирует и отправляет меню выбора мастеров после успешной отмены.
     * Позволяет пользователю мгновенно перейти к созданию новой записи.
     */
    private void sendStaffSelectionMenu(MessageSender messageSender, Long chatId, String slug) {
        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String companyId = barbershop.getYclientsCompanyId();
            List<StaffDto> staffList = yclientsService.getFreshStaff(companyId);

            if (staffList == null || staffList.isEmpty()) {
                messageSender.sendMessage(chatId, "⚠️ Нет доступных мастеров.", null);
                return;
            }

            staffList.removeIf(staff -> !Boolean.TRUE.equals(staff.getBookable()));

            if (staffList.isEmpty()) {
                messageSender.sendMessage(chatId, "⚠️ Нет доступных мастеров.", null);
                return;
            }

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (StaffDto staff : staffList) {
                String buttonText = staff.getName();
                String specialization = staff.getSpecialization();
                if (specialization != null && !specialization.isEmpty()) {
                    buttonText += " (" + specialization + ")";
                }

                rows.add(List.of(
                        InlineKeyboardButton.builder()
                                .text(buttonText)
                                .callbackData("staff_" + staff.getId() + "_" + slug)
                                .build()
                ));
            }

            try {
                messageSender.sendMessage(
                        chatId,"👨‍🔧 Выберите мастера:",
                        InlineKeyboardMarkup.builder()
                                .keyboard(rows)
                                .build()
                );
            } catch (Exception e) {
                log.error("Ошибка при отправке меню мастеров для chatId={}", chatId, e);
            }
        }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден.", null));
    }

}