package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.enums.BookingStatus;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Обработчик раздела "Мои записи".
 * <p>
 * Класс агрегирует историю клиента, выполняет сортировку по времени (descending)
 * и ограничивает выборку последними 5 событиями для предотвращения спама в чате.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MyBookingsHandler {

    private final ClientService clientService;
    private final BookingService bookingService;

    /**
     * Формирует список карточек бронирования.
     * <p>
     * Для каждой записи создается отдельное сообщение. Это позволяет прикреплять
     * инлайн-кнопку "Отменить" индивидуально к каждой активной брони.
     */
    public void handle(Long chatId, TelegramMessageSender sender) {
        clientService.findByTelegramId(chatId).ifPresentOrElse(client -> {
            List<Booking> allBookings = bookingService.getAllBookings(client);
            log.info("Найдено {} записей для клиента {} (chatId={})", allBookings.size(), client.getId(), chatId);

            // Фильтрация дубликатов по record_id и record_hash
            Set<String> seenRecords = new java.util.HashSet<>();
            OffsetDateTime now = OffsetDateTime.now();
            List<Booking> bookings = allBookings.stream()
                    .filter(b -> {
                        boolean isValid =
                                b.getStatus() == BookingStatus.PENDING ||
                                        b.getStatus() == BookingStatus.CONFIRMED ||
                                        b.getStatus() == BookingStatus.COMPLETED;
                        String recordKey = (b.getRecordId() != null ? b.getRecordId().toString() : "null") + "_" +
                                (b.getRecordHash() != null ? b.getRecordHash() : "null");
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

                String formattedDate = booking.getDatetime()
                        .atZoneSameInstant(zoneId)
                        .format(outputFormatter);

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

                SendMessage msg;
                // Показываем кнопку "Отменить" только для PENDING или CONFIRMED
                // и если время не прошло (с защитой от null)
                boolean canCancel = (booking.getStatus() == BookingStatus.PENDING ||
                        booking.getStatus() == BookingStatus.CONFIRMED) &&
                        booking.getRecordId() != null &&
                        booking.getRecordHash() != null &&
                        !booking.getRecordHash().isEmpty() &&
                        (booking.getEndTime() != null) && booking.getEndTime().isAfter(now);

                if (canCancel) {
                    InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                            .text("❌ Отменить эту запись")
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
                    if (booking.getStatus() == BookingStatus.COMPLETED) {
                        text.append("\nℹ️ Эта запись уже выполнена.");
                    } else if (booking.getEndTime() != null && booking.getEndTime().isBefore(now)) {
                        text.append("\nℹ️ Эта запись уже завершена и не может быть отменена.");
                    } else {
                        log.warn("Booking ID={} для chatId={} не имеет recordId/recordHash/endTime, отмена недоступна",
                                booking.getId(), chatId);
                        text.append("\n⚠️ Отмена недоступна: ");
                        if (booking.getEndTime() == null) {
                            text.append("время окончания не указано.");
                        } else {
                            text.append("отсутствует идентификатор записи.");
                        }
                    }
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

    /**
     * Абстракция над механизмом отправки сообщений.
     * Позволяет тестировать логику отображения без прямой зависимости от Telegram API.
     */
    // Вспомогательный интерфейс, чтобы не тянуть в этот класс TelegramBot
    public interface TelegramMessageSender {
        /**
         * Отправка простого текстового сообщения.
         */
        void sendMessage(Long chatId, String text);

        /**
         * Выполнение сложного метода отправки (с клавиатурой, разметкой и т.д.).
         */
        void executeMessage(SendMessage message) throws TelegramApiException;
    }
}
