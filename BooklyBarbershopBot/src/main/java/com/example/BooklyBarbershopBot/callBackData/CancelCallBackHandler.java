package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.entity.Barbershop;
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
     * @param bot экземпляр TelegramBot
     */
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

            log.info("Запись отменена, отправляем меню выбора услуг, slug: {}", booking.getSlug());
            //Предложить выбрать услугу для новой записи
            sendServiceSelectionMenu(bot, chatId, booking.getSlug());
        } else {
            sendMessage(bot, chatId, "❌ Не удалось отменить запись. Попробуйте позже.");
            log.error("Не удалось отменить запись recordId: {}", booking.getRecordId());
        }
    }

    /**
     * Отправляет пользователю меню выбора услуги для новой записи.
     *
     * @param bot экземпляр TelegramBot
     * @param chatId идентификатор чата
     * @param slug уникальный идентификатор барбершопа
     */
    private void sendServiceSelectionMenu(TelegramBot bot, Long chatId, String slug) {

        String companyId = barbershopService.getBySlug(slug)
                .map(Barbershop::getYclientsCompanyId)
                .orElse(null);
        log.error("Получаем компанию по slug: {}", slug);

        if (companyId == null) {
            sendMessage(bot, chatId, "❌ Барбершоп не найден.");
            return;
        }

        //метод чтобы Получить список услуг для барбершопа
        List<ServiceDto> services = yclientsService.getServices(companyId);
        log.info("Услуги для компании {}: {}", companyId, services);

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

    /**
     * Вспомогательный метод для отправки сообщений пользователю.
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
