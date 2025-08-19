package com.example.BooklyBarbershopBot.telegramBot;

import com.example.BooklyBarbershopBot.callBackData.CallBack;
import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.handlers.BookingConfirmationService;
import com.example.BooklyBarbershopBot.handlers.MenuCommandHandler;
import com.example.BooklyBarbershopBot.handlers.MyBookingsHandler;
import com.example.BooklyBarbershopBot.handlers.PhoneNumberRequestService;
import com.example.BooklyBarbershopBot.inlineButtons.InlineKeyboard;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.sendMessage.TelegramMessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.ClientService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основной класс Telegram-бота, реализующий функционал обработки входящих сообщений через долгие опросы (Long Polling).
 * <p>
 * Этот бот реагирует на текстовые сообщения, в частности на команду <code>/start</code> с возможным параметром —
 * уникальным идентификатором (slug) барбершопа. По этому идентификатору бот ищет данные в сервисе {@link BarbershopService}
 * и отправляет пользователю приветственное сообщение с информацией о найденном барбершопе.
 * <p>
 * Если барбершоп с заданным идентификатором не найден, бот уведомляет об этом пользователя.
 * Если команда <code>/start</code> вызывается без параметров, бот просит уточнить ссылку на барбершоп.
 * <p>
 * Использует Spring для внедрения зависимостей и конфигурации имени и токена бота из application.properties.
 * <p>
 * Логирование ошибок происходит через {@code SLF4J}.
 *
 * @see org.telegram.telegrambots.bots.TelegramLongPollingBot
 * @see BarbershopService
 */

@SuppressWarnings("LoggingSimilarMessage")
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBot extends TelegramLongPollingBot implements MessageSender {

    /**
     * Сервис для работы с данными барбершопов.
     */
    private final BarbershopService barbershopService;
    private final InlineKeyboard inlineKeyboard;
    private final CallBack handleCallBack;

    @Getter
    private final Map<Long, BookingData> bookingCache = new ConcurrentHashMap<>();

    //Сервисы для хранения данных клиентов и записей
    private final ClientService clientService;
    private final MyBookingsHandler myBookingsHandler;
    private final MenuCommandHandler menuCommandHandler;
    private final PhoneNumberRequestService phoneNumberRequestService;
    private final BookingConfirmationService bookingConfirmationService;
    @Value("${telegrambots.bots[0].username}")
    private String botUsername;
    @Value("${telegrambots.bots[0].token}")
    private String botToken;

    @PostConstruct
    public void initCallbackLink() {
        handleCallBack.setTelegramBot(this);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder().chatId(chatId.toString()).text(text).build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }


    /**
     * Обрабатывает входящие обновления (Update) от Telegram.
     * <p>
     * Если обновление содержит текстовое сообщение, оно передаётся в метод {@link #handleTextMessage(Message)} для дальнейшей обработки.
     *
     * @param update объект обновления, полученный от Telegram
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallBack.handleCallback(update.getCallbackQuery());
        }
    }

    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.hasText() ? message.getText().trim() : null;

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
        bookingConfirmationService.confirmBooking(chatId, data, smsCode, new TelegramMessageSender() {
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

        if (text.startsWith("/start")) {
            String[] parts = text.split(" ", 2);
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                String slug = parts[1].trim();
                log.info("Парсинг slug: {} для chatId={}", slug, chatId);

                barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                    log.info("Барбершоп найден: slug={}, name={} для chatId={}", slug, barbershop.getName(), chatId);
                    // Сохраняем slug в клиенте
                    clientService.saveOrUpdateLastUsedSlug(chatId, slug);

                    String greeting = barbershop.getGreeting() != null ? barbershop.getGreeting() : "Добро пожаловать!";
                    String fullText = "🏪 " + barbershop.getName() + "\n\n" + greeting;

                    SendMessage response = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(fullText)
                            .replyMarkup(inlineKeyboard.createMenuInlineKeyboard(slug))
                            .build();

                    try {
                        execute(response);
                        log.info("Приветственное сообщение отправлено для slug={} и chatId={}", slug, chatId);
                    } catch (TelegramApiException e) {
                        log.error("Ошибка при отправке приветствия для slug={} и chatId={}", slug, chatId, e);
                    }
                }, () -> {
                    log.warn("Барбершоп с slug={} не найден для chatId={}", slug, chatId);
                    try {
                        execute(SendMessage.builder()
                                .chatId(chatId.toString())
                                .text("❌ Барбершоп с slug '" + slug + "' не найден.")
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("Ошибка при отправке сообщения о ненайденном барбершопе для chatId={}", chatId, e);
                    }
                });


            } else {
                // Обработка /start без slug
                log.info("Обработка /start без slug для chatId={}", chatId);
                try {
                    execute(SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("""
                                    👋 Добро пожаловать! Чтобы начать, введите команду /start и добавьте через пробел уникальное название барбершопа. Например: /start <barbershop-...>
                                    
                                    Это название указывает, в какой барбершоп вы хотите записаться. Вы также можете ввести /menu, чтобы посмотреть доступные действия.""")
                            .build());
                    log.info("Сообщение для /start без slug отправлено для chatId={}", chatId);
                } catch (TelegramApiException e) {
                    log.error("Ошибка при отправке сообщения для /start без slug для chatId={}", chatId, e);
                }
            }

        }
    }
}