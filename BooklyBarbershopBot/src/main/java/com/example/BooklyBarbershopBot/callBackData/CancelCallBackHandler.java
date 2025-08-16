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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик callback-запросов для отмены записи.
 * <p>
 * Обрабатывает callbackData, начинающиеся с "cancel_".
 * Выполняет отмену активной записи пользователя через Yclients API,
 * обновляет статус записи в базе и предлагает выбрать новую услугу для записи.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCallBackHandler implements CallBackHandler {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final YclientsService yclientsService;
    private final BarbershopService barbershopService;


    /**
     * Проверяет, начинается ли callbackData с "cancel_".
     *
     * @param data данные callback
     * @return true, если поддерживает обработку отмены записи
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("cancel_");
    }

    /**
     * Обрабатывает callbackQuery, отменяет активную запись пользователя,
     * обновляет статус записи и предлагает выбрать новую услугу.
     *
     * @param callbackQuery объект callbackQuery
     * @param bot           экземпляр TelegramBot
     */
    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        try {
            String[] parts = data.split("_", 3);
            if (parts.length < 3) {
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                log.warn("Некорректный callbackData: {} для chatId={}", data, chatId);
                return;
            }
            String recordIdStr = parts[1];
            String recordHash = parts[2];

            if (recordIdStr == null || recordIdStr.equals("null") || recordHash == null || recordHash.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Ошибка: отсутствует идентификатор записи или хэш.");
                log.warn("Недопустимые recordId={} или recordHash={} для chatId={}", recordIdStr, recordHash, chatId);
                return;
            }

            Long recordId = Long.parseLong(parts[1]);

            Optional<Client> optionalClient = clientService.findByTelegramId(chatId);
            if (optionalClient.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Клиент не найден.");
                return;
            }

            Optional<Booking> optionalBooking = bookingService.findByRecordIdAndRecordHash(recordId, recordHash);
            if (optionalBooking.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Запись не найдена.");
                return;
            }

            Booking booking = optionalBooking.get();
            if (!booking.getClient().getTelegramId().equals(chatId)) {
                sendMessage(bot, chatId, "⚠️ Эта запись не принадлежит вам.");
                return;
            }

            if (!"PENDING".equals(booking.getStatus()) && !"CONFIRMED".equals(booking.getStatus())) {
                sendMessage(bot, chatId, "⚠️ Эта запись уже отменена или неактивна.");
                return;
            }

            boolean success = yclientsService.cancelBooking(recordId, recordHash);
            if (success) {
                bookingService.updateBookingStatus(booking, "CANCELLED");
                bot.getBookingCache().remove(chatId);
                sendMessage(bot, chatId, "✅ Запись отменена.");

                // Предложить выбрать новую услугу
                sendStaffSelectionMenu(bot, chatId, booking.getSlug());
            } else {
                sendMessage(bot, chatId, "❌ Не удалось отменить запись. Попробуйте позже.");
                log.error("Не удалось отменить запись recordId={}", recordId);
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке отмены записи для chatId={}", chatId, e);
            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    private void sendStaffSelectionMenu(TelegramBot bot, Long chatId, String slug) {
        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String companyId = barbershop.getYclientsCompanyId();
            List<StaffDto> staffList = yclientsService.getFreshStaff(companyId);

            if (staffList == null || staffList.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Нет доступных мастеров.");
                return;
            }

            staffList.removeIf(staff -> !Boolean.TRUE.equals(staff.getBookable()));

            if (staffList.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Нет доступных мастеров.");
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
                log.error("Ошибка при отправке меню мастеров", e);
            }
        }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));
    }

    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения для chatId={}", chatId, e);
        }
    }
}
//    @Override
//    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
//        Long chatId = callbackQuery.getMessage().getChatId();
//        cancelBookingForChat(bot, chatId);
//    }
//
//    /**
//     * Универсальный метод отмены записи для команды /cancel и кнопки
//     */
//    public void cancelBookingForChat(TelegramBot bot, Long chatId) {
//        Optional<Client> optionalClient = clientService.findByTelegramId(chatId);
//        if (optionalClient.isEmpty()) {
//            sendMessage(bot, chatId, "⚠️ Клиент не найден.");
//            return;
//        }
//        Client client = optionalClient.get();
//
//        Optional<Booking> optionalBooking = bookingService.getActiveBooking(client);
//        if (optionalBooking.isEmpty()) {
//            sendMessage(bot, chatId, "⚠️ Нет записи, которую можно отменить.");
//            return;
//        }
//        Booking booking = optionalBooking.get();
//
//        boolean success = yclientsService.cancelBooking(booking.getRecordId(), booking.getRecordHash());
//        if (success) {
//            bookingService.updateBookingStatus(booking, "CANCELLED");
//            bot.getBookingCache().remove(chatId);
//
//            sendMessage(bot, chatId, "✅ Запись отменена.");
//
//            log.info("Запись отменена, отправляем меню выбора услуг, slug: {}", booking.getSlug());
//            // Предложить выбрать новую услугу
//            sendStaffSelectionMenu(bot, chatId, booking.getSlug());
//        } else {
//            sendMessage(bot, chatId, "❌ Не удалось отменить запись. Попробуйте позже.");
//            log.error("Не удалось отменить запись recordId: {}", booking.getRecordId());
//        }
//    }
//
//
//    /**
//     * Отправляет пользователю меню выбора услуги для новой записи.
//     *
//     * @param bot    экземпляр TelegramBot
//     * @param chatId идентификатор чата
//     * @param slug   уникальный идентификатор барбершопа
//     */
//    private void sendStaffSelectionMenu(TelegramBot bot, Long chatId, String slug) {
//
//        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
//            String companyId = barbershop.getYclientsCompanyId();
//
//            // Получаем свежий список мастеров
//            List<StaffDto> staffList = yclientsService.getFreshStaff(companyId);
//
//            if (staffList == null || staffList.isEmpty()) {
//                sendMessage(bot, chatId, "⚠️ Нет доступных мастеров.");
//                return;
//            }
//
//            // Фильтруем только тех, кто доступен для записи
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
//                log.error("Ошибка при отправке меню мастеров", e);
//            }
//        }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));
//    }
//
//    /**
//     * Вспомогательный метод для отправки сообщений пользователю.
//     *
//     * @param bot    экземпляр TelegramBot
//     * @param chatId идентификатор чата
//     * @param text   текст сообщения
//     */
//    private void sendMessage(TelegramBot bot, Long chatId, String text) {
//        try {
//            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
//        } catch (Exception e) {
//            log.error("Ошибка при отправке сообщения", e);
//        }
//    }

