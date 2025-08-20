package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.eventService.BotEventService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Класс-распределитель callback-запросов от Telegram.
 * <p>
 * Получает входящие callbackQuery и делегирует их обработку первому подходящему обработчику
 * из списка {@link CallBackHandler}. Если ни один обработчик не поддерживает данные callback,
 * отправляет пользователю сообщение о неизвестной команде.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallBack {

    /**
     * Список обработчиков callback-запросов
     */
    private final List<CallBackHandler> handlers;

    private final BotEventService botEventService;
    private final BookingService bookingService;

    /**
     * Ссылка на основной Telegram-бот для отправки сообщений
     */
    @Setter
    private TelegramBot telegramBot;

    /**
     * Основной метод обработки callback-запроса.
     * Делегирует обработку первому обработчику, поддерживающему данные.
     *
     * @param callbackQuery входящий callbackQuery от Telegram
     */
    public void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        // Обработка отмены записи
        if (data.startsWith("cancel_")) {
            String[] parts = data.split("_");
            if (parts.length == 3) {
                try {
                    Long recordId = Long.parseLong(parts[1]);
                    String recordHash = parts[2];

                    // Отменяем запись
                    bookingService.cancelBooking(recordId, recordHash);

                    // Логируем событие BOOKING_CANCELLED
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("recordId", recordId);
                    eventData.put("recordHash", recordHash);
                    UUID barbershopId = bookingService.getBookingByRecordId(recordId)
                            .map(booking -> bookingService.getBarbershopIdBySlug(booking.getSlug()))
                            .orElse(null);
                    botEventService.saveEvent(chatId, barbershopId, "BOOKING_CANCELLED", eventData);

                    telegramBot.sendMessage(chatId, "Запись успешно отменена.");
                } catch (IllegalArgumentException | IllegalStateException e) {
                    log.error("Ошибка при отмене записи для chatId={}: {}", chatId, e.getMessage());
                    telegramBot.sendMessage(chatId, "Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    log.error("Неожиданная ошибка при отмене записи для chatId={}: {}", chatId, e.getMessage());
                    telegramBot.sendMessage(chatId, "❌ Произошла ошибка при отмене записи. Попробуйте позже.");
                }
            } else {
                log.warn("Неверный формат callbackData: {} для chatId={}", data, chatId);
                telegramBot.sendMessage(chatId, "Ошибка: неверный формат данных.");
            }
            return;
        }

        for (CallBackHandler handler : handlers) {
            if (handler.supports(data)) {
                try {
                    handler.handle(callbackQuery, telegramBot);
                } catch (Exception e) {
                    log.error("Ошибка при обработке callbackData={} для chatId={}", data, chatId, e);
                    try {
                        telegramBot.execute(SendMessage.builder()
                                .chatId(chatId.toString())
                                .text("❌ Произошла ошибка. Попробуйте позже.")
                                .build());
                    } catch (Exception ex) {
                        log.error("Ошибка при отправке сообщения об ошибке для chatId={}", chatId, ex);
                    }
                }
                return;
            }
        }

        log.warn("Не найден обработчик для callbackData={} и chatId={}", data, chatId);
        try {
            telegramBot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Некорректный запрос.")
                    .build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения о некорректном запросе для chatId={}", chatId, e);
        }
    }

}