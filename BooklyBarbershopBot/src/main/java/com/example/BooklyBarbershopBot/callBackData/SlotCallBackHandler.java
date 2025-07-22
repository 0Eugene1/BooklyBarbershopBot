package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Обработчик callback-запросов для выбора конкретного временного слота записи.
 * <p>
 * Поддерживает callbackData, начинающиеся с "slot_".
 * Извлекает дату, время, идентификаторы мастера и услуги, а также slug барбершопа.
 * Создаёт объект BookingData с выбранными параметрами, сохраняет его в кеш бронирования,
 * запрашивает у пользователя ввод имени.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;

    /**
     * Проверяет, начинается ли callbackData с "slot_".
     *
     * @param data данные callback
     * @return true, если поддерживается обработка выбора временного слота
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("slot_");
    }

    /**
     * Обрабатывает callbackQuery, восстанавливает дату и время из callbackData,
     * получает имена мастера и услуги, сохраняет данные в кеш бронирования,
     * запрашивает у пользователя ввод имени.
     *
     * @param callbackQuery объект callbackQuery
     * @param bot           экземпляр TelegramBot
     */
    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 6);
            if (parts.length < 5) {
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            String date = parts[1];        // 2025-07-01
            String time = parts[2];        // 17-15
            Long staffId = Long.parseLong(parts[3]);
            Long serviceId = Long.parseLong(parts[4]);
            String slug = parts.length >= 6 ? parts[5] : "";

            String datetime = restoreIsoDatetime(date + "_" + time); // → "2025-07-01T17:15:00"

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();

                String staffName = yclientsService.getStaffName(companyId, staffId);
                String serviceName = yclientsService.getServiceName(companyId, serviceId);

                BookingData bookingData = new BookingData();

                bookingData.setSlug(slug);
                bookingData.setServiceId(serviceId);
                bookingData.setStaffId(staffId);
                bookingData.setDatetime(datetime);
                bookingData.setStaffName(staffName);
                bookingData.setServiceName(serviceName);
                bookingData.setPhone(null);
                bookingData.setAwaitingCode(false);
                bookingData.setRecordId(null);
                bookingData.setRecordHash(null);
                bookingData.setFullName(null);

                // ЗАСТАВЛЯЕМ СНАЧАЛА ВВЕСТИ ИМЯ
                bookingData.setAwaitingFullName(true);

                bot.getBookingCache().put(chatId, bookingData);


                // Отправляем сообщение с просьбой ввести имя
                SendMessage message = SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("✅ Вы выбрали:\n" +
                                "• Дата и время: " + formatUserFriendlyDatetime(datetime) + "\n" +
                                "• Мастер: " + staffName + "\n" +
                                "• Услуга: " + serviceName + "\n\n" +
                                "Пожалуйста, введите своё имя.")
                        .build();

                try {
                    bot.execute(message);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка при обработке callback slot_", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка при выборе времени.");
        }
    }

    /**
     * Форматирует ISO дату-время в удобочитаемый вид dd/MM/yyyy HH:mm.
     *
     * @param isoDatetime дата-время в формате ISO
     * @return форматированная строка
     */
    public String formatUserFriendlyDatetime(String isoDatetime) {
        LocalDateTime dateTime = LocalDateTime.parse(isoDatetime); // парсим ISO-строку
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return dateTime.format(formatter); // форматируем в нужный вид
    }

    /**
     * Восстанавливает ISO строку даты-времени из безопасного формата с подчеркиванием и дефисом.
     *
     * @param safeDatetime строка формата "yyyy-MM-dd_HH-mm"
     * @return строка формата "yyyy-MM-ddTHH:mm:ss"
     */
    private String restoreIsoDatetime(String safeDatetime) {
        String[] parts = safeDatetime.split("_");
        String date = parts[0]; // "2025-07-01"
        String time = parts[1].replace("-", ":"); // "17:15"
        return date + "T" + time + ":00";
    }

    /**
     * Вспомогательный метод для отправки сообщения пользователю.
     *
     * @param bot экземпляр TelegramBot
     * @param chatId идентификатор чата
     * @param text текст сообщения
     */
    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}
