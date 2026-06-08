package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.enums.BookingStatus;
import com.example.BooklyBarbershopBot.globalException.YclientsSmsConfirmationException;
import com.example.BooklyBarbershopBot.sendMessage.TelegramMessageSender;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.BookingStateService;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.service.yclientsService.YclientsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сервис финализации бронирования.
 * <p>
 * Координирует взаимодействие между локальной базой данных и внешним API Yclients.
 * Отвечает за:
 * 1. Регистрацию/обновление данных клиента.
 * 2. Создание локальной записи о визите.
 * 3. Подтверждение записи во внешней системе (включая обработку SMS-кодов).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationService {

    private final ClientService clientService;
    private final BookingService bookingService;
    private final YclientsService yclientsService;
    private final BookingStateService bookingStateService;

    /**
     * Выполняет финальное подтверждение записи.
     * <p>
     * Если метод вызывается первый раз, Yclients может выбросить исключение требования SMS.
     * При повторном вызове с переданным {@code smsCode} запись подтверждается окончательно.
     *
     * @param chatId  ID чата пользователя.
     * @param data    объект с накопленными данными о записи (мастер, время, услуги).
     * @param smsCode код подтверждения (может быть null при первой попытке).
     * @param sender  компонент для отправки уведомлений пользователю.
     * @return {@code true}, если запись подтверждена в Yclients и локально переведена в CONFIRMED.
     */
    public boolean confirmBooking(Long chatId, BookingData data, String smsCode, TelegramMessageSender sender) {
        log.info("Начало подтверждения записи для chatId: {}, мастерId: {}", chatId, data.getStaffId());

        try {
            Client client = clientService.saveOrGetClient(
                    data.getPhone(),
                    chatId,
                    data.getFullName(),
                    "no-reply@example.com"
            );

            Booking booking = bookingService.resolvePendingBooking(client, data);
            data.setPendingBookingId(booking.getId());

            boolean success = yclientsService.createBooking(data, client, smsCode);

            if (success) {
                booking.setRecordId(data.getRecordId());
                booking.setRecordHash(data.getRecordHash());
                booking.setStatus(BookingStatus.CONFIRMED);

                bookingService.saveBooking(booking);
                bookingStateService.remove(chatId);
                data.setAwaitingCode(false);

                sender.sendMessage(chatId, "✅ Запись подтверждена! Ждем вас в назначенное время.");
                log.info("Запись успешно подтверждена: recordId={}", data.getRecordId());
                return true;
            }

            sender.sendMessage(chatId, "❌ Не удалось подтвердить запись. Проверьте код или попробуйте позже.");
            return false;

        } catch (YclientsSmsConfirmationException e) {
            data.setAwaitingCode(true);
            sender.sendMessage(chatId, "🔐 Ваш номер требует подтверждения. Пожалуйста, введите код из SMS, который мы отправили.");
            log.info("Требуется подтверждение по SMS для chatId: {}, pendingBookingId={}",
                    chatId, data.getPendingBookingId());
            return false;

        } catch (Exception e) {
            log.error("Критическая ошибка при подтверждении записи для chatId={}", chatId, e);
            sender.sendMessage(chatId, "❌ Произошла ошибка при создании записи. Пожалуйста, свяжитесь с администратором.");
            return false;
        }
    }
}
