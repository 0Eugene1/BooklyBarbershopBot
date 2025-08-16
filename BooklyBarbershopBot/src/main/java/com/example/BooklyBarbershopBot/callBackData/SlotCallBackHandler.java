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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

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
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

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


    //FIXME TEST 11.08
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
                BookingData bookingData = bot.getBookingCache().get(chatId);
                if (bookingData == null) {
                    bookingData = new BookingData();
                }

                bookingData.setSlug(slug);
                bookingData.setStaffId(staffId);
                bookingData.setDatetime(offsetDateTime);
                bookingData.setStaffName(yclientsService.getStaffName(companyId, staffId));

                // --- новый блок ---
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
// --- конец нового блока ---

                bot.getBookingCache().put(chatId, bookingData);

                bookingData.setAwaitingFullName(true);
                bot.getBookingCache().put(chatId, bookingData);
//                 --- Создаём новый BookingData для текущей записи ---


                StringBuilder sb = new StringBuilder();
                sb.append("<b>✅ Вы выбрали дату и время:</b>\n")
                        .append("⏰ <i>").append(formatUserFriendlyDatetime(bookingData.getDatetime())).append("</i>\n")
                        .append("💈 <b>Мастер:</b> ").append(bookingData.getStaffName()).append("\n");

                if (bookingData.getServiceNames() != null && !bookingData.getServiceNames().isEmpty()) {
                    sb.append("✂️ <b>Услуги:</b> ")
                            .append(String.join(", ", bookingData.getServiceNames()))
                            .append("\n")
                            .append("• • • • • • • • • • • • • •\n")  // лёгкая разделительная линия
                            .append("Пожалуйста, введите своё имя."); // один раз
                }

                SendMessage message = SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(sb.toString())
                        .parseMode("HTML") // важно!
                        .build();
                try {
                    bot.execute(message);
                } catch (Exception e) {
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
     * @param datetime дата-время в формате ISO
     * @return форматированная строка
     */
    public String formatUserFriendlyDatetime(OffsetDateTime datetime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm");
        return datetime.toLocalDateTime().format(formatter); // форматируем в нужный вид
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
     * @param bot    экземпляр TelegramBot
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}
