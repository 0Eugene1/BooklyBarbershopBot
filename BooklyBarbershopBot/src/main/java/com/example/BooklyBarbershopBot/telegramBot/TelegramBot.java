package com.example.BooklyBarbershopBot.telegramBot;

import com.example.BooklyBarbershopBot.callBackData.CallBack;
import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.dto.RecordInfo;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.globalException.YclientsSmsConfirmationException;
import com.example.BooklyBarbershopBot.inlineButtons.InlineKeyboard;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingService;
import com.example.BooklyBarbershopBot.service.ClientService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    /**
     * Сервис для работы с данными барбершопов.
     */
    private final BarbershopService barbershopService;
    private final InlineKeyboard inlineKeyboard;
    private final CallBack handleCallBack;
    private final YclientsService yclientsService;
    @Getter
    private final Map<Long, BookingData> bookingCache = new ConcurrentHashMap<>();
    //временное хранилище
    private final Map<Long, RecordInfo> recordCache = new ConcurrentHashMap<>();

    //Сервисы для хранения данных клиентов и записей
    private final ClientService clientService;
    private final BookingService bookingService;


    @PostConstruct
    public void initCallbackLink() {
        handleCallBack.setTelegramBot(this);
    }

    @Value("${telegrambots.bots[0].username}")
    private String botUsername;

    @Value("${telegrambots.bots[0].token}")
    private String botToken;

    @Override
    public String getBotUsername() {
        return botUsername;
        }

    @Override
    public String getBotToken() {
        return botToken;
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
            Message message = update.getMessage();
            Long chatId = message.getChatId();

            String text = message.hasText() ? message.getText().trim() : null;

            // --- 1. Обрабатываем команды /start, /cancel без проверки BookingData

            if (text != null) {
                if (text.equals("/cancel")) {
                    handleCancelCommand(chatId);
                    return;
                }

                if (text.startsWith("/start")) {
                    handleTextMessage(message); // у тебя тут обработка /start с параметром slug
                    return;
                }
            }

            // --- 2. Получаем текущий BookingData из кеша
            BookingData data = bookingCache.get(chatId);

            // --- 3. Если есть bookingData, обрабатываем ввод имени, телефона, SMS-кода

            if (data != null) {

                // Если ожидаем ввод полного имени
                if (data.isAwaitingFullName() && text != null) {
                    data.setFullName(text);
                    data.setAwaitingFullName(false);
                    bookingCache.put(chatId, data);

                    sendRequestPhoneNumberKeyboard(chatId);
                    log.info("Сохраняем имя в BookingData: {}", text);
                    return;
                }

                // Если ожидаем ввод SMS-кода (код подтверждения из SMS)
                if (data.isAwaitingCode() && text != null) {
                    Client client = clientService.saveOrGetClient(data.getPhone(), chatId, data.getFullName(), "no-reply@example.com");
                    try {
                        log.info("Клиент fullname: {}", client.getFullName());
                        boolean success = yclientsService.createBooking(data, client, text); // с SMS-кодом

                        if (success) {
                            saveRecordInfo(chatId, data.getRecordId(), data.getRecordHash());
                            bookingService.saveBooking(client, data);
                            sendMessage(chatId, "✅ Запись подтверждена!");
                            bookingCache.remove(chatId);
                        } else {
                            sendMessage(chatId, "❌ Не удалось подтвердить запись. Проверьте код.");
                        }
                    } catch (Exception e) {
                        log.error("Ошибка при подтверждении записи по SMS", e);
                        sendMessage(chatId, "❌ Ошибка при подтверждении записи.");
                    }
                    return;
                }

                // Обработка контакта (номер телефона)
                if (message.hasContact()) {
                    String phone = message.getContact().getPhoneNumber();
                    data.setPhone(phone);

                    Client client = clientService.saveOrGetClient(phone, chatId, data.getFullName(), "no-reply@example.com");
                    bookingCache.put(chatId, data);


                    try {
                        log.info("Клиент fullname: {}", client.getFullName());
                        boolean success = yclientsService.createBooking(data, client, null); // без SMS-кода

                        if (success) {
                            saveRecordInfo(chatId, data.getRecordId(), data.getRecordHash());
                            bookingService.saveBooking(client, data);
                            sendMessage(chatId, "✅ Запись успешно создана! Мы ждём вас в указанное время.");
                            bookingCache.remove(chatId);
                        } else {
                            sendMessage(chatId, "❌ Не удалось создать запись. Попробуйте позже.");
                        }
                    } catch (YclientsSmsConfirmationException e) {
                        data.setAwaitingCode(true);
                        bookingCache.put(chatId, data);
                        sendMessage(chatId, "🔐 Ваш номер требует подтверждения. Пожалуйста, введите код из SMS.");
                    } catch (Exception e) {
                        log.error("Ошибка при создании брони", e);
                        sendMessage(chatId, "❌ Ошибка при создании записи.");
                    }
                    return;
                }

                // Если имя еще не заполнено (на всякий случай, если ты хочешь, чтобы бот сам запросил)
                if ((data.getFullName() == null || data.getFullName().isEmpty()) && !data.isAwaitingFullName()) {
                    data.setAwaitingFullName(true);
                    bookingCache.put(chatId, data);
                    sendMessage(chatId, "Пожалуйста, введите своё имя.");
                    return;
                }
            }

            // --- 4. Если не выполнено ни одно из условий выше, передаём управление в общий обработчик
            if (text != null) {
                handleTextMessage(message);
            }
        }

        if (update.hasCallbackQuery()) {
            handleCallBack.handelCallback(update.getCallbackQuery());
        }
    }

    // Вспомогательный метод для отправки запроса номера телефона
    private void sendRequestPhoneNumberKeyboard(Long chatId) {
        KeyboardButton contactButton = new KeyboardButton("📱 Отправить номер");
        contactButton.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(contactButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text("Спасибо! Теперь отправьте, пожалуйста, свой номер телефона.")
                .replyMarkup(markup)
                .build();

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке клавиатуры для номера телефона", e);
        }
    }


    private void saveRecordInfo(Long chatId, Long recordId, String recordHash) {
        // Например, сохранить в мапу или базу
        log.info("💾 Сохраняем запись: chatId={}, recordId={}, recordHash={}", chatId, recordId, recordHash);
        recordCache.put(chatId, new RecordInfo(recordId, recordHash));
    }


    private void handleCancelCommand(Long chatId) {
        // Очистим кеш записи
        bookingCache.remove(chatId);

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text("❌ Запись отменена. Вы можете начать заново с команды /start")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения отмены", e);
        }
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
            String[] parts = text.split(" ");
            if (parts.length > 1) {
                String slug = parts[1];
                log.info("Парсинг slug: {}", slug);

                barbershopService.getBySlug(slug).ifPresentOrElse(
                        barbershop -> {
                            String greeting = barbershop.getGreeting() != null
                                    ? barbershop.getGreeting()
                                    : "Добро пожаловать!";
                            log.info("START command received with slug: {}", slug);

                            String fullText = "🏪 " + barbershop.getName() + "\n\n" + greeting;

                            SendMessage response = new SendMessage();
                            response.setChatId(chatId.toString());
                            response.setText(fullText);
                            response.setReplyMarkup(inlineKeyboard.createMenuInlineKeyboard(slug)); // вот это

                            try {
                                execute(response);
                            } catch (TelegramApiException e) {
                                log.error("Ошибка при отправке приветствия", e);
                            }
                        },
                        () -> {
                            try {
                                execute(new SendMessage(chatId.toString(), "❌ Барбершоп не найден."));
                            } catch (TelegramApiException e) {
                                log.error("Ошибка при ответе", e);
                            }
                        }
                );
            } else {
                try {
                    execute(new SendMessage(chatId.toString(), "👋 Привет! Уточните ссылку на барбершоп."));
                } catch (TelegramApiException e) {
                    log.error("Ошибка при приветствии", e);
                }
            }
        }
    }
    public void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
        try {
            this.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка при отправке сообщения", e);
        }
    }

}
