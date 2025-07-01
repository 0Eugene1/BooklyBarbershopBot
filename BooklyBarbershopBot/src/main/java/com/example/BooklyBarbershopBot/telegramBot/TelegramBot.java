package com.example.BooklyBarbershopBot.telegramBot;

import com.example.BooklyBarbershopBot.callBackData.CallBack;
import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.inlineButtons.InlineKeyboard;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
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
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        }
        if (update.hasCallbackQuery()) {
            handleCallBack.handelCallback(update.getCallbackQuery());
        }
        if (update.hasMessage() && update.getMessage().hasContact()) {
            Long chatId = update.getMessage().getChatId();
            Contact contact = update.getMessage().getContact();
            String phone = contact.getPhoneNumber();

            BookingData data = bookingCache.get(chatId);
            if (data == null) {
                sendMessage(chatId, "⚠️ Сначала выберите услугу и время.");
                return;
            }

            // 👉 Подготовка данных для создания брони
            log.info("Создание брони: {}, телефон: {}", data, phone);

            try {
                boolean success = yclientsService.createBooking(
                        data.getSlug(),
                        data.getServiceId(),
                        data.getStaffId(),
                        data.getDatetime(),
                        phone
                );

                if (success) {
                    sendMessage(chatId, "✅ Запись успешно создана! Мы ждём вас в указанное время.");
                } else {
                    sendMessage(chatId, "❌ Не удалось создать запись. Попробуйте позже.");
                }

                bookingCache.remove(chatId); // удаляем временные данные

            } catch (Exception e) {
                log.error("Ошибка при создании брони", e);
                sendMessage(chatId, "❌ Ошибка при создании записи.");
            }
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
