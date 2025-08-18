package com.example.BooklyBarbershopBot.config;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


/**
 * Конфигурационный класс Spring, который инициализирует и регистрирует Telegram-бота в Telegram Bots API.
 * <p>
 * В рамках этого класса создаётся бин {@link TelegramBotsApi}, который управляет сессией бота и
 * позволяет принимать обновления (updates) через механизм Long Polling.
 * <p>
 * Используется {@link DefaultBotSession} для запуска сессии по умолчанию.
 * <p>
 * При создании бина TelegramBotsApi происходит регистрация переданного экземпляра {@link TelegramBot}
 * в Telegram API, что позволяет боту начать принимать и обрабатывать сообщения.
 * <p>
 * Исключения, связанные с API Telegram, пробрасываются наружу для последующей обработки Spring.
 *
 * @see TelegramBotsApi
 * @see TelegramBot
 * @see DefaultBotSession
 */

@Configuration
public class TelegramBotConfig {

    @Bean
    public DefaultBotOptions defaultBotOptions() {
        DefaultBotOptions botOptions = new DefaultBotOptions();
        botOptions.setMaxThreads(2); // Уменьшаем число потоков для минимизации конфликтов
        botOptions.setGetUpdatesTimeout(20); // Таймаут поллинга 20 секунд
        return botOptions;
    }

    /**
     * Создаёт и регистрирует экземпляр {@link TelegramBotsApi} с переданным ботом {@link TelegramBot}.
     * <p>
     * Это позволяет Telegram-боту начать получать обновления и взаимодействовать с пользователями.
     *
     * @param telegramBot экземпляр бота, который будет зарегистрирован в Telegram API
     * @return объект TelegramBotsApi, управляющий регистрацией и получением обновлений для бота
     * @throws TelegramApiException исключение, выбрасываемое при ошибках регистрации бота в Telegram API
     */
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBot telegramBot) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(telegramBot);
        return botsApi;
    }
}
