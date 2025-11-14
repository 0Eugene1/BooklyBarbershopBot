package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.service.eventService.BotEventService;
import com.example.BooklyBarbershopBot.service.yclientsService.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCallBackHandler implements CallBackHandler {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final YclientsService yclientsService;
    private final BarbershopService barbershopService;
    private final BotEventService boteventService;

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

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        Optional<Client> optionalClient = clientService.findByTelegramId(chatId);
        if (optionalClient.isEmpty()) {
            sendMessage(bot, chatId, "⚠️ Клиент не найден.", null);
            return;
        }

        String[] parts = data.split("_", 3);
        if (parts.length < 3) {
            sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.", null);
            log.warn("Некорректный callbackData: {} для chatId={}", data, chatId);
            return;
        }

        String recordIdStr = parts[1];
        String recordHash = parts[2];

        if (recordIdStr == null || recordIdStr.equals("null") || recordHash == null || recordHash.isEmpty()) {
            sendMessage(bot, chatId, "⚠️ Ошибка: отсутствует идентификатор записи или хэш.", null);
            log.warn("Недопустимые recordId={} или recordHash={} для chatId={}", recordIdStr, recordHash, chatId);
            return;
        }

        Long recordId;
        try {
            recordId = Long.parseLong(recordIdStr);
        } catch (NumberFormatException e) {
            sendMessage(bot, chatId, "⚠️ Ошибка: некорректный идентификатор записи.", null);
            log.warn("Некорректный recordId={} для chatId={}", recordIdStr, chatId);
            return;
        }

        Optional<Booking> optionalBooking = bookingService.findByRecordIdAndRecordHash(recordId, recordHash);
        if (optionalBooking.isEmpty()) {
            sendMessage(bot, chatId, "⚠️ Запись не найдена.", null);
            return;
        }

        Booking booking = optionalBooking.get();
        if (!booking.getClient().getTelegramId().equals(chatId)) {
            sendMessage(bot, chatId, "⚠️ Эта запись не принадлежит вам.", null);
            return;
        }

        // Форматтер для даты, как в MyBookingsHandler
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm");
        ZoneId zoneId = ZoneId.of("Europe/Moscow"); // Укажите ваш часовой пояс
        String formattedDate = booking.getDatetime()
                .atZoneSameInstant(zoneId)
                .format(outputFormatter);

        // Отображаем статус в стиле MyBookingsHandler
        String statusDisplay = switch (booking.getStatus()) {
            case "PENDING" -> "Ожидает подтверждения";
            case "CONFIRMED" -> "Подтверждено";
            case "COMPLETED" -> "Выполнено";
            default -> booking.getStatus();
        };

        StringBuilder text = new StringBuilder();
        text.append("💈 *Мастер*: ").append(booking.getStaffName()).append("\n");
        text.append("✂️ *Услуги*: ").append(booking.getServiceName()).append("\n");
        text.append("⏰ *Дата и время*: ").append(formattedDate).append("\n");
        text.append("Статус: ").append(statusDisplay);

        // Проверяем статус и дату
        OffsetDateTime now = OffsetDateTime.now();
        if ("COMPLETED".equals(booking.getStatus())) {
            text.append("\nℹ️ Эта запись уже выполнена.");
            sendMessage(bot, chatId, text.toString(), "Markdown");
            return;
        } else if (booking.getDatetime().isBefore(now)) {
            text.append("\nℹ️ Эта запись просрочена и не может быть отменена.");
            bookingService.updateBookingStatus(booking, "COMPLETED");
            sendMessage(bot, chatId, text.toString(), "Markdown");
            return;
        } else if (!"PENDING".equals(booking.getStatus()) && !"CONFIRMED".equals(booking.getStatus())) {
            text.append("\nℹ️ Эта запись уже отменена или не подлежит отмене.");
            sendMessage(bot, chatId, text.toString(), "Markdown");
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
            sendMessage(bot, chatId, "❌ Ошибка: барбершоп не найден.", null);
            return;
        }
        // Пытаемся отменить запись
        try {
            boolean success = yclientsService.cancelBooking(recordId, recordHash);
            if (success) {
                bookingService.updateBookingStatus(booking, "CANCELLED");
                bot.getBookingCache().remove(chatId);

                //Сохранение события
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("recordId", recordId);
                eventData.put("recordHash", recordHash);
                boteventService.saveEvent(chatId, barbershopId, "BOOKING_CANCELLED", eventData);
                sendMessage(bot, chatId, "✅ Запись отменена.", null);
                // Предлагаем выбрать новую услугу
                sendStaffSelectionMenu(bot, chatId, booking.getSlug());
            } else {
                bookingService.updateBookingStatus(booking, "COMPLETED");
                text.append("\nℹ️ Эта запись уже выполнена и не может быть отменена.");
                sendMessage(bot, chatId, text.toString(), "Markdown");
                log.error("Не удалось отменить запись recordId={} для chatId={}", recordId, chatId);
            }
        } catch (Exception e) {
            log.error("Ошибка при вызове Yclients API для отмены записи recordId={} для chatId={}: {}", recordId, chatId, e.getMessage());
            if (e.getMessage().contains("403") || e.getMessage().contains("Запись подтверждена в филиале")) {
                bookingService.updateBookingStatus(booking, "COMPLETED");
                text.append("\nℹ️ Эта запись уже выполнена и не может быть отменена.");
                sendMessage(bot, chatId, text.toString(), "Markdown");
            } else {
                sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.", null);
            }
        }
    }

    private void sendStaffSelectionMenu(TelegramBot bot, Long chatId, String slug) {
        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String companyId = barbershop.getYclientsCompanyId();
            List<StaffDto> staffList = yclientsService.getFreshStaff(companyId);

            if (staffList == null || staffList.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Нет доступных мастеров.", null);
                return;
            }

            staffList.removeIf(staff -> !Boolean.TRUE.equals(staff.getBookable()));

            if (staffList.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Нет доступных мастеров.", null);
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

            SendMessage msg = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("👨‍🔧 Выберите мастера:")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                    .build();

            try {
                bot.execute(msg);
            } catch (Exception e) {
                log.error("Ошибка при отправке меню мастеров для chatId={}", chatId, e);
            }
        }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден.", null));
    }

    private void sendMessage(TelegramBot bot, Long chatId, String text, String parseMode) {
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build();
            if (parseMode != null) {
                msg.setParseMode(parseMode);
            }
            bot.execute(msg);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения для chatId={}", chatId, e);
        }
    }
}
