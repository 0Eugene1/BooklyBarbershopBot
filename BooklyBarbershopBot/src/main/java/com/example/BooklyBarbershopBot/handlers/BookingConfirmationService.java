package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.globalException.YclientsSmsConfirmationException;
import com.example.BooklyBarbershopBot.sendMessage.TelegramMessageSender;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationService {

    private final ClientService clientService;
    private final BookingService bookingService;
    private final YclientsService yclientsService;

    public void confirmBooking(Long chatId, BookingData data, String smsCode, TelegramMessageSender sender) {
        try {
            // 1. Создаем или получаем клиента
            Client client = clientService.saveOrGetClient(data.getPhone(), chatId, data.getFullName(), "no-reply@example.com");

            // 2. Создаем новую запись вместо поиска активной
            Booking booking = bookingService.createBookingFromData(client, data);

            // 3. Создаем запись в Yclients
            boolean success = yclientsService.createBooking(data, client, smsCode);

            if (success) {
                booking.setRecordId(data.getRecordId());
                booking.setRecordHash(data.getRecordHash());
                booking.setStatus("CONFIRMED");
                bookingService.saveBooking(booking);

                sender.sendMessage(chatId, "✅ Запись подтверждена!");
            } else {
                sender.sendMessage(chatId, "❌ Не удалось подтвердить запись. Проверьте код или попробуйте позже.");
            }
        } catch (YclientsSmsConfirmationException e) {
            data.setAwaitingCode(true);
            sender.sendMessage(chatId, "🔐 Ваш номер требует подтверждения. Пожалуйста, введите код из SMS.");
        } catch (Exception e) {
            log.error("Ошибка при подтверждении записи для chatId={}", chatId, e);
            sender.sendMessage(chatId, "❌ Ошибка при создании или подтверждении записи.");
        }
    }
}
