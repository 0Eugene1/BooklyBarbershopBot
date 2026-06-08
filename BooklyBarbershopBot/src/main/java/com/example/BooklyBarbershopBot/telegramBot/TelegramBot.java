package com.example.BooklyBarbershopBot.telegramBot;

import com.example.BooklyBarbershopBot.callBackData.CallBack;
import com.example.BooklyBarbershopBot.callBackData.LowRatingReasonCallBackHandler;
import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.handlers.BookingConfirmationService;
import com.example.BooklyBarbershopBot.handlers.MenuCommandHandler;
import com.example.BooklyBarbershopBot.handlers.MyBookingsHandler;
import com.example.BooklyBarbershopBot.handlers.PhoneNumberRequestService;
import com.example.BooklyBarbershopBot.inlineButtons.InlineKeyboard;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.sendMessage.TelegramMessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingStateService;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.service.eventService.BotEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Центральный контроллер Telegram-бота.
 * <p>
 * Координирует работу всех подсистем: от парсинга команд и обработки нажатий на кнопки
 * до сбора персональных данных клиента (имя, телефон) и фиксации событий аналитики.
 */
@SuppressWarnings("LoggingSimilarMessage")
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final CallBack callBack;
    private final MessageSender messageSender;
    /**
     * Сервис для работы с данными барбершопов.
     */
    private final BarbershopService barbershopService;
    private final InlineKeyboard inlineKeyboard;


    private final BookingStateService bookingCache;

    //Сервисы для хранения данных клиентов и записей
    private final ClientService clientService;
    private final MyBookingsHandler myBookingsHandler;
    private final MenuCommandHandler menuCommandHandler;
    private final PhoneNumberRequestService phoneNumberRequestService;
    private final BookingConfirmationService bookingConfirmationService;
    private final BotEventService botEventService;

    private final LowRatingReasonCallBackHandler lowRatingReasonCallBackHandler;


    @Value("${telegrambots.bots[0].username}")
    private String botUsername;

    @Value("${telegrambots.bots[0].token}")
    private String botToken;


    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getBotToken() {
        return botToken;
    }


    public void sendMessage(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке", e);
        }
    }


    /**
     * Точка входа для всех обновлений из Telegram.
     * Разделяет входящий трафик на CallbackQuery (кнопки) и Message (текст/контакты).
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            callBack.handleCallback(update.getCallbackQuery());
            return;
        }

        if (update.hasMessage()) {
            handleMessage(update.getMessage());
        }
    }


    /**
     * Обрабатывает текстовые сообщения и контакты.
     * <p>
     * Сначала проверяет наличие глобальных команд (/start, /menu),
     * затем проверяет, не находится ли пользователь в процессе заполнения данных бронирования.
     */
    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText().trim() : null;

        if (text != null && lowRatingReasonCallBackHandler.handleTextReason(chatId, text, messageSender)) {
            return;
        }

        if (text != null && handleCommands(chatId, message, text)) {
            return; // если это была команда, обработка завершена
        }

        BookingData data = bookingCache.get(chatId);
        if (data != null && handleBookingData(chatId, message, data, text)) {
            return;// если обработка BookingData завершена, выходим
        }
    }

    private boolean handleCommands(Long chatId, Message message, String text) {
        if (text.startsWith("/start")) {
            handleTextMessage(message);
            return true;
        }

        return switch (text) {
            case "/cancel" -> {
                handleCancelCommand(chatId);
                yield true;
            }
            case "/menu" -> {
                handleMenuCommand(chatId);
                yield true;
            }
            case "/mybookings" -> {
                handleMyBookingsCommand(chatId);
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleBookingData(Long chatId, Message message, BookingData data, String text) {
        if (data.isAwaitingFullName() && text != null) {
            data.setFullName(text);
            data.setAwaitingFullName(false);
            bookingCache.put(chatId, data);
            sendRequestPhoneNumberKeyboard(chatId);
            log.info("Сохраняем имя в BookingData: {}", text);
            return true;
        }

        if (message.hasContact()) {
            data.setPhone(message.getContact().getPhoneNumber());
            bookingCache.put(chatId, data);
            confirmBooking(chatId, data, null);
            return true;
        }

        if (data.isAwaitingCode() && text != null) {
            confirmBooking(chatId, data, text);
            return true;
        }

        if ((data.getFullName() == null || data.getFullName().isEmpty()) && !data.isAwaitingFullName()) {
            data.setAwaitingFullName(true);
            bookingCache.put(chatId, data);
            sendMessage(chatId, "Пожалуйста, введите своё имя.");
            return true;
        }

        return false;
    }


    private void sendRequestPhoneNumberKeyboard(Long chatId) {
        phoneNumberRequestService.sendRequestPhoneNumberKeyboard(chatId, new TelegramMessageSender() {
            @Override
            public void sendMessage(Long chatId, String text) {
                TelegramBot.this.sendMessage(chatId, text);
            }

            @Override
            public void executeMessage(SendMessage message) throws TelegramApiException {
                execute(message);
            }
        });
    }

    private void confirmBooking(Long chatId, BookingData data, String smsCode) {
        try {
            boolean confirmed = bookingConfirmationService.confirmBooking(chatId, data, smsCode, new TelegramMessageSender() {
                @Override
                public void sendMessage(Long chatId, String text) {
                    TelegramBot.this.sendMessage(chatId, text);
                }

                @Override
                public void executeMessage(SendMessage message) throws TelegramApiException {
                    execute(message);
                }
            });

            if (confirmed) {
                saveBookingCreatedEvent(chatId, data);
            } else {
                bookingCache.put(chatId, data);
            }
        } catch (Exception e) {
            log.error("Failed to confirm booking for chatId={}", chatId, e);
            sendMessage(chatId, "Ошибка при подтверждении бронирования. Попробуйте снова.");
        }
    }

    private void saveBookingCreatedEvent(Long chatId, BookingData data) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("fullName", data.getFullName());
        eventData.put("phone", data.getPhone());
        eventData.put("serviceNames", data.getServiceNames());
        eventData.put("datetime", data.getDatetime() != null ? data.getDatetime().toString() : null);
        eventData.put("staffId", data.getStaffId());
        eventData.put("staffName", data.getStaffName());
        eventData.put("slug", data.getSlug());
        eventData.put("recordId", data.getRecordId());
        eventData.put("recordHash", data.getRecordHash());
        eventData.put("pendingBookingId", data.getPendingBookingId());
        UUID barbershopId = barbershopService.getBySlug(data.getSlug())
                .map(Barbershop::getId)
                .orElse(null);
        botEventService.saveEvent(chatId, barbershopId, "BOOKING_CREATED", eventData);
    }



    private void handleMyBookingsCommand(Long chatId) {
        myBookingsHandler.handle(chatId, new MyBookingsHandler.TelegramMessageSender() {
            @Override
            public void sendMessage(Long chatId, String text) {
                TelegramBot.this.sendMessage(chatId, text);
            }

            @Override
            public void executeMessage(SendMessage message) throws TelegramApiException {
                execute(message);
            }
        });
    }

    public void handleMenuCommand(Long chatId) {
        menuCommandHandler.handle(chatId, bookingCache, new MenuCommandHandler.TelegramMessageSender() {
            @Override
            public void sendMessage(Long chatId, String text) {
                TelegramBot.this.sendMessage(chatId, text);
            }

            @Override
            public void executeMessage(SendMessage message) throws TelegramApiException {
                execute(message);
            }
        });
    }

    private void handleCancelCommand(Long chatId) {
        bookingCache.remove(chatId);
        myBookingsHandler.handle(chatId, new MyBookingsHandler.TelegramMessageSender() {
            @Override
            public void sendMessage(Long chatId, String text) {
                TelegramBot.this.sendMessage(chatId, text);
            }

            @Override
            public void executeMessage(SendMessage message) throws TelegramApiException {
                execute(message);
            }
        });
        sendMessage(chatId, "Выберите запись для отмены из списка выше.");
    }

    /**
     * Обрабатывает текстовое сообщение пользователя.
     * <p>
     * Если сообщение начинается с команды <code>/start</code> и содержит параметр (slug барбершопа),
     * бот пытается найти соответствующий барбершоп через {@link BarbershopService}.
     * <ul>
     * <li>Если барбершоп найден — отправляет приветственное сообщение с названием и приветствием барбершопа.</li>
     * <li>Если не найден — отправляет сообщение об ошибке.</li>
     * </ul>
     * Если параметр отсутствует, бот просит пользователя уточнить ссылку.
     * <p>
     * Все сообщения отправляются в чат пользователя, который инициировал диалог.
     *
     * @param message входящее сообщение от пользователя
     */
    private void handleTextMessage(Message message) {
        String text = message.getText();
        Long chatId = message.getChatId();

        log.info("Получено сообщение: {}", text);

        if (text == null || text.isBlank()) {
            return;
        }

        // =========================
        // /start command handling
        // =========================
        if (text.startsWith("/start")) {
            String[] parts = text.split(" ", 2);

            // /start <slug>
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                String slug = parts[1].trim();
                handleStartWithSlug(chatId, slug);
                return;
            }

            // /start (без slug)
            clientService.getLastUsedSlug(chatId).ifPresentOrElse(
                    lastSlug -> {
                        log.info("Используем lastUsedSlug={} для chatId={}", lastSlug, chatId);
                        handleStartWithSlug(chatId, lastSlug);
                    },
                    () -> {
                        log.info("lastUsedSlug не найден, показываем стартовый экран");
                        sendStartWithoutSlug(chatId);
                    }
            );
            return;
        }
    }

        private void sendStartWithoutSlug(Long chatId) {
            try {
                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("""
    👋 Добро пожаловать!
    
    Этот бот помогает записаться в барбершоп онлайн ✂️
    
    Чтобы начать запись:
    1️⃣ Перейдите по ссылке на нужный барбершоп
    2️⃣ Бот сразу откроет его страницу и покажет меню
    
    📢 Список подключённых барбершопов доступен в нашем официальном канале.
    
    Если у вас уже есть код барбершопа, вы можете ввести:
    <code>/start barbershop-name</code>
    """)
                        .replyMarkup(inlineKeyboard.startWithoutSlugKeyboard())
                        .parseMode("HTML")
                        .build());
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке /start без slug", e);
            }
        }

    private void handleStartWithSlug(Long chatId, String slug) {
        log.info("Парсинг slug: {} для chatId={}", slug, chatId);

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            log.info("Барбершоп найден: slug={}, name={}", slug, barbershop.getName());

            clientService.saveOrUpdateLastUsedSlug(chatId, slug);

            String greeting = barbershop.getGreeting() != null
                    ? barbershop.getGreeting()
                    : "Добро пожаловать!";

            String fullText = "🏪 " + barbershop.getName() + "\n\n" + greeting;

            try {
                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(fullText)
                        .replyMarkup(inlineKeyboard.createMenuInlineKeyboard(slug))
                        .build());

                Map<String, Object> eventData = new HashMap<>();
                eventData.put("slug", slug);
                eventData.put("barbershopName", barbershop.getName());
                botEventService.saveEvent(chatId, barbershop.getId(), "USER_STARTED", eventData);

            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке приветствия", e);
            }

        }, () -> sendInvalidSlug(chatId, slug));
    }
    private void sendInvalidSlug(Long chatId, String slug) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("""
❌ Барбершоп не найден.

Пожалуйста, выберите барбершоп из списка 👇
""")
                    .replyMarkup(inlineKeyboard.startWithoutSlugKeyboard())
                    .build());

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("slug", slug);
            eventData.put("action", "invalid_slug");
            botEventService.saveEvent(chatId, null, "USER_STARTED_INVALID_SLUG", eventData);

        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения о неверном slug", e);
        }
    }

}