package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.RecordInfo;
import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.entity.Booking;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.BookingStorageService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCallBackHandler implements CallbackHandler {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final YclientsService yclientsService;
    private final BarbershopService barbershopService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("cancel_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        Long chatId = callbackQuery.getMessage().getChatId();

        Optional<Client> optionalClient = clientService.findByTelegramId(chatId);
        if (optionalClient.isEmpty()) {
            sendMessage(bot, chatId, "⚠️ Клиент не найден.");
            return;
        }
        Client client = optionalClient.get();

        Optional<Booking> optionalBooking = bookingService.getActiveBooking(client);
        if (optionalBooking.isEmpty()) {
            sendMessage(bot, chatId, "⚠️ Нет записи, которую можно отменить.");
            return;
        }
        Booking booking = optionalBooking.get();

        boolean success = yclientsService.cancelBooking(booking.getRecordId(), booking.getRecordHash());
        if (success) {
            bookingService.updateBookingStatus(booking, "CANCELLED");
            sendMessage(bot, chatId, "✅ Запись отменена.");

            //Предложить выбрать услугу для новой записи
            sendServiceSelectionMenu(bot, chatId, booking.getSlug());
        } else {
            sendMessage(bot, chatId, "❌ Не удалось отменить запись. Попробуйте позже.");
        }
    }
    private void sendServiceSelectionMenu(TelegramBot bot, Long chatId, String slug) {

        String companyId = barbershopService.getBySlug(slug)
                .map(b -> b.getYclientsCompanyId())
                .orElse(null);

        if (companyId == null) {
            sendMessage(bot, chatId, "❌ Барбершоп не найден.");
            return;
        }

        List<ServiceDto> services = yclientsService.getServices(companyId);

        if (services == null || services.isEmpty()) {
            sendMessage(bot, chatId, "⚠️ Нет доступных услуг для записи.");
            return;
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ServiceDto service : services) {
            rows.add(List.of(
                    InlineKeyboardButton.builder()
                            .text(service.getTitle())
                            .callbackData("select_service_" + service.getId() + "_" + slug)
                            .build()
            ));
        }

        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text("📝 Выберите услугу для новой записи:")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();

        try {
            bot.execute(msg);
        } catch (Exception e) {
            log.error("Ошибка при отправке меню выбора услуги", e);
        }
    }


    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}

