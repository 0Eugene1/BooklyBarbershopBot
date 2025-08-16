package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class MyBookingsHandler {

    private final ClientService clientService;
    private final BookingService bookingService;

    public void handle(Long chatId, TelegramMessageSender sender) {
        clientService.findByTelegramId(chatId).ifPresentOrElse(client -> {
            List<Booking> allBookings = bookingService.getAllBookings(client);
            log.info("Найдено {} записей для клиента {} (chatId={})", allBookings.size(), client.getId(), chatId);

            // Фильтрация дубликатов по record_id и record_hash
            Set<String> seenRecords = new java.util.HashSet<>();
            List<Booking> bookings = allBookings.stream()
                    .filter(b -> {
                        boolean isValid = "PENDING".equals(b.getStatus()) || "CONFIRMED".equals(b.getStatus());
                        String recordKey = b.getRecordId() + "_" + b.getRecordHash();
                        boolean isUnique = seenRecords.add(recordKey);
                        log.info("Booking ID={} recordId={} recordHash={} status={} datetime={} isValid={} isUnique={}",
                                b.getId(), b.getRecordId(), b.getRecordHash(), b.getStatus(), b.getDatetime(), isValid, isUnique);
                        return isValid && isUnique;
                    })
                    .sorted((b1, b2) -> {
                        if (b1.getDatetime() == null || b2.getDatetime() == null) {
                            log.warn("Booking ID={} или ID={} имеет null datetime", b1.getId(), b2.getId());
                            return 0;
                        }
                        return b2.getDatetime().compareTo(b1.getDatetime());
                    })
                    .limit(5)
                    .toList();

            if (bookings.isEmpty()) {
                sender.sendMessage(chatId, "У вас пока нет активных записей.");
                return;
            }

            // Форматтер с указанием часового пояса
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm");
            ZoneId zoneId = ZoneId.of("Europe/Moscow"); // Укажите ваш часовой пояс, например, +03:00

            for (Booking booking : bookings) {
                if (booking.getDatetime() == null) {
                    log.warn("Booking ID={} для chatId={} имеет null datetime", booking.getId(), chatId);
                    sender.sendMessage(chatId, "⚠️ Ошибка: запись ID=" + booking.getId() + " не содержит даты.");
                    continue;
                }

                // Преобразование Timestamp в ZonedDateTime с нужным часовым поясом
                String formattedDate = booking.getDatetime()
                        .toInstant()
                        .atZone(zoneId)
                        .format(outputFormatter);

                StringBuilder text = new StringBuilder();
                text.append("💈 *Мастер*: ").append(booking.getStaffName()).append("\n");
                text.append("✂️ *Услуги*: ").append(booking.getServiceName()).append("\n");
                text.append("⏰ *Дата и время*: ").append(formattedDate).append("\n");
                text.append("Статус: ").append(booking.getStatus());

                SendMessage msg;
                if ("PENDING".equals(booking.getStatus()) || "CONFIRMED".equals(booking.getStatus())) {
                    if (booking.getRecordId() != null && booking.getRecordHash() != null && !booking.getRecordHash().isEmpty()) {
                        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                                .text("❌ Отменить")
                                .callbackData("cancel_" + booking.getRecordId() + "_" + booking.getRecordHash())
                                .build();
                        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                                .keyboard(List.of(List.of(cancelButton)))
                                .build();

                        msg = SendMessage.builder()
                                .chatId(chatId.toString())
                                .text(text.toString())
                                .replyMarkup(markup)
                                .parseMode("Markdown")
                                .build();
                    } else {
                        log.warn("Booking ID={} для chatId={} имеет null recordId или recordHash", booking.getId(), chatId);
                        msg = SendMessage.builder()
                                .chatId(chatId.toString())
                                .text(text.toString() + "\n⚠️ Отмена недоступна: отсутствует идентификатор записи.")
                                .parseMode("Markdown")
                                .build();
                    }
                } else {
                    msg = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(text.toString())
                            .parseMode("Markdown")
                            .build();
                }

                try {
                    sender.executeMessage(msg);
                } catch (TelegramApiException e) {
                    log.error("Ошибка при отправке записи ID={} для chatId={}", booking.getId(), chatId, e);
                }
            }
        }, () -> {
            log.warn("Клиент не найден для chatId={}", chatId);
            sender.sendMessage(chatId, "⚠️ Клиент не найден.");
        });
    }

//    public void handle(Long chatId, TelegramMessageSender sender) {
//        clientService.findByTelegramId(chatId).ifPresentOrElse(client -> {
//            List<Booking> bookings = bookingService.getAllBookings(client).stream()
//                    .filter(b -> "PENDING".equals(b.getStatus()) || "CONFIRMED".equals(b.getStatus()))
//                    .sorted((b1, b2) -> b2.getDatetime().compareTo(b1.getDatetime()))
//                    .limit(5)
//                    .toList();
//
//            if (bookings.isEmpty()) {
//                sender.sendMessage(chatId, "У вас пока нет активных записей.");
//                return;
//            }
//
//            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm");
//            //TODO CHECK HOW IT WORKS 16 08
//            for (Booking booking : bookings) {
//                String formattedDate = booking.getDatetime()
//                        .toLocalDateTime()
//                        .format(outputFormatter);
//                System.out.println(formattedDate);
//
//                StringBuilder text = new StringBuilder();
//                text.append("💈 *Мастер*: ").append(booking.getStaffName()).append("\n");
//                text.append("✂️ *Услуги*: ").append(booking.getServiceName()).append("\n");
//                text.append("⏰ *Дата и время*: ").append(formattedDate).append("\n");
//                text.append("Статус: ").append(booking.getStatus());
//
//                SendMessage msg;
//                if ("PENDING".equals(booking.getStatus()) || "CONFIRMED".equals(booking.getStatus())) {
//                    InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
//                            .text("❌ Отменить")
//                            .callbackData("cancel_" + booking.getRecordId() + "_" + booking.getRecordHash())
//                            .build();
//                    InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
//                            .keyboard(List.of(List.of(cancelButton)))
//                            .build();
//
//                    msg = SendMessage.builder()
//                            .chatId(chatId.toString())
//                            .text(text.toString())
//                            .replyMarkup(markup)
//                            .parseMode("Markdown")
//                            .build();
//                } else {
//                    msg = SendMessage.builder()
//                            .chatId(chatId.toString())
//                            .text(text.toString())
//                            .parseMode("Markdown")
//                            .build();
//                }
//
//                try {
//                    sender.executeMessage(msg);
//                } catch (TelegramApiException e) {
//                    log.error("Ошибка при отправке записи", e);
//                }
//            }
//        }, () -> sender.sendMessage(chatId, "⚠️ Клиент не найден."));
//    }

    // Вспомогательный интерфейс, чтобы не тянуть в этот класс TelegramBot
    public interface TelegramMessageSender {
        void sendMessage(Long chatId, String text);
        void executeMessage(SendMessage message) throws TelegramApiException;
    }
}
