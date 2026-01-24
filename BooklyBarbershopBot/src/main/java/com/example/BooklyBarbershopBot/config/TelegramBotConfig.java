package com.example.BooklyBarbershopBot.config;

import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import com.example.BooklyBarbershopBot.telegramBot.adminBot.AdminBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Главный конфигурационный класс для Telegram-ботов системы.
 * <p>
 * Создает необходимые бины для работы библиотеки telegrambots-spring-boot-starter.
 * Отвечает за:
 * 1. Настройку параметров соединения и многопоточности.
 * 2. Регистрацию клиентского и административного ботов в едином API-сервисе.
 */
@Configuration
@Slf4j
public class TelegramBotConfig {

    /**
     * Создает настройки по умолчанию для ботов.
     * <p>
     * Здесь задаются параметры:
     * - {@code maxThreads}: ограничение количества потоков для обработки входящих обновлений.
     * - {@code getUpdatesTimeout}: время ожидания ответа от сервера (long polling).
     *
     * @return настроенный объект {@link DefaultBotOptions}.
     */
    @Bean
    public DefaultBotOptions defaultBotOptions() {
        DefaultBotOptions botOptions = new DefaultBotOptions();
        botOptions.setMaxThreads(2);
        botOptions.setGetUpdatesTimeout(20);
        return botOptions;
    }

    /**
     * Инициализирует и регистрирует ботов в системе Telegram.
     * <p>
     * Метод автоматически вызывается Spring при старте приложения.
     * Если регистрация одного из ботов завершится ошибкой, приложение выбросит {@link TelegramApiException}.
     *
     * @param userBot  экземпляр клиентского бота (инъекция бина).
     * @param adminBot экземпляр административного бота (инъекция бина).
     * @return объект {@link TelegramBotsApi} с зарегистрированными ботами.
     * @throws TelegramApiException если регистрация в Telegram API не удалась.
     */
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBot userBot, AdminBot adminBot) throws TelegramApiException {
        log.debug("Инициализация TelegramBotsApi...");
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {
            botsApi.registerBot(userBot);
            log.info("Основной бот @{} успешно зарегистрирован", userBot.getBotUsername());

            botsApi.registerBot(adminBot);
            log.info("Админ-бот @{} успешно зарегистрирован", adminBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Критическая ошибка при регистрации ботов: {}", e.getMessage());
            throw e;
        }

        return botsApi;
    }
}
