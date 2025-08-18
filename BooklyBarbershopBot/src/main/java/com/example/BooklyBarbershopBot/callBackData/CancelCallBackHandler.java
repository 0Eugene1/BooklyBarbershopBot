package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCallBackHandler implements CallBackHandler {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final YclientsService yclientsService;
    private final BarbershopService barbershopService;

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

        Client client = optionalClient.get();
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

        // Пытаемся отменить запись
        try {
            boolean success = yclientsService.cancelBooking(recordId, recordHash);
            if (success) {
                bookingService.updateBookingStatus(booking, "CANCELLED");
                bot.getBookingCache().remove(chatId);
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
                rows.add(List.of(
                        InlineKeyboardButton.builder()
                                .text(staff.getName())
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
//    @Override
//    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
//        Long chatId = callbackQuery.getMessage().getChatId();
//        String data = callbackQuery.getData();
//
//        Optional<Client> optionalClient = clientService.findByTelegramId(chatId);
//        if (optionalClient.isEmpty()) {
//            sendMessage(bot, chatId, "⚠️ Клиент не найден.");
//            return;
//        }
//
//        Client client = optionalClient.get();
//        List<Booking> activeBookings = bookingService.getAllBookings(client).stream()
//                .filter(b -> ("PENDING".equals(b.getStatus()) || "CONFIRMED".equals(b.getStatus()))
//                        && b.getRecordId() != null && b.getRecordHash() != null && !b.getRecordHash().isEmpty())
//                .toList();
//
//        if (activeBookings.isEmpty()) {
//            sendMessage(bot, chatId, "⚠️ У вас нет активных записей для отмены.");
//            return;
//        }
//
//        try {
//            String[] parts = data.split("_", 3);
//            if (parts.length < 3) {
//                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
//                log.warn("Некорректный callbackData: {} для chatId={}", data, chatId);
//                return;
//            }
//            String recordIdStr = parts[1];
//            String recordHash = parts[2];
//
//            if (recordIdStr == null || recordIdStr.equals("null") || recordHash == null || recordHash.isEmpty()) {
//                sendMessage(bot, chatId, "⚠️ Ошибка: отсутствует идентификатор записи или хэш.");
//                log.warn("Недопустимые recordId={} или recordHash={} для chatId={}", recordIdStr, recordHash, chatId);
//                return;
//            }
//
//            Long recordId;
//            try {
//                recordId = Long.parseLong(recordIdStr);
//            } catch (NumberFormatException e) {
//                sendMessage(bot, chatId, "⚠️ Ошибка: некорректный идентификатор записи.");
//                log.warn("Некорректный recordId={} для chatId={}", recordIdStr, chatId);
//                return;
//            }
//
//            Optional<Booking> optionalBooking = bookingService.findByRecordIdAndRecordHash(recordId, recordHash);
//            if (optionalBooking.isEmpty()) {
//                sendMessage(bot, chatId, "⚠️ Запись не найдена.");
//                return;
//            }
//
//            Booking booking = optionalBooking.get();
//            if (!booking.getClient().getTelegramId().equals(chatId)) {
//                sendMessage(bot, chatId, "⚠️ Эта запись не принадлежит вам.");
//                return;
//            }
//
//            if (!"PENDING".equals(booking.getStatus()) && !"CONFIRMED".equals(booking.getStatus())) {
//                sendMessage(bot, chatId, "⚠️ Эта запись уже отменена или выполнена.");
//                return;
//            }
//
//            // Проверка, не прошла ли дата записи
//            if (booking.getDatetime().isBefore(OffsetDateTime.now())) {
//                bookingService.updateBookingStatus(booking, "COMPLETED");
//                sendMessage(bot, chatId, "ℹ️ Эта запись уже выполнена или просрочена.");
//                return;
//            }
//
//            try {
//                boolean success = yclientsService.cancelBooking(recordId, recordHash);
//                if (success) {
//                    bookingService.updateBookingStatus(booking, "CANCELLED");
//                    bot.getBookingCache().remove(chatId);
//                    sendMessage(bot, chatId, "✅ Запись отменена.");
//
//                    // Предложить выбрать новую услугу
//                    sendStaffSelectionMenu(bot, chatId, booking.getSlug());
//                } else {
//                    sendMessage(bot, chatId, "❌ Не удалось отменить запись. Возможно, она уже выполнена.");
//                    log.error("Не удалось отменить запись recordId={} для chatId={}", recordId, chatId);
//                    bookingService.updateBookingStatus(booking, "COMPLETED");
//                }
//            } catch (Exception e) {
//                log.error("Ошибка при вызове Yclients API для отмены записи recordId={} для chatId={}: {}", recordId, chatId, e.getMessage());
//                if (e.getMessage().contains("403") || e.getMessage().contains("Запись подтверждена в филиале")) {
//                    bookingService.updateBookingStatus(booking, "COMPLETED");
//                    sendMessage(bot, chatId, "ℹ️ Эта запись уже выполнена и не может быть отменена.");
//                } else {
//                    sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
//                }
//            }
//        } catch (Exception e) {
//            log.error("Ошибка при обработке отмены записи для chatId={}", chatId, e);
//            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
//        }
//    }
//
//    private void sendStaffSelectionMenu(TelegramBot bot, Long chatId, String slug) {
//        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
//            String companyId = barbershop.getYclientsCompanyId();
//            List<StaffDto> staffList = yclientsService.getFreshStaff(companyId);
//
//            if (staffList == null || staffList.isEmpty()) {
//                sendMessage(bot, chatId, "⚠️ Нет доступных мастеров.");
//                return;
//            }
//
//            staffList.removeIf(staff -> !Boolean.TRUE.equals(staff.getBookable()));
//
//            if (staffList.isEmpty()) {
//                sendMessage(bot, chatId, "⚠️ Нет доступных мастеров.");
//                return;
//            }
//
//            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
//            for (StaffDto staff : staffList) {
//                rows.add(List.of(
//                        InlineKeyboardButton.builder()
//                                .text(staff.getName())
//                                .callbackData("staff_" + staff.getId() + "_" + slug)
//                                .build()
//                ));
//            }
//
//            SendMessage msg = SendMessage.builder()
//                    .chatId(chatId.toString())
//                    .text("👨‍🔧 Выберите мастера:")
//                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
//                    .build();
//
//            try {
//                bot.execute(msg);
//            } catch (Exception e) {
//                log.error("Ошибка при отправке меню мастеров для chatId={}", chatId, e);
//            }
//        }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));
//    }
//
//    private void sendMessage(TelegramBot bot, Long chatId, String text) {
//        try {
//            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
//        } catch (Exception e) {
//            log.error("Ошибка при отправке сообщения для chatId={}", chatId, e);
//        }
    }
