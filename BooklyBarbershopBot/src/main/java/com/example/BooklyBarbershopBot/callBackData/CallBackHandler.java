package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Базовый интерфейс для всех обработчиков callback-событий бота.
 * <p>
 * Используется в рамках паттерна "Стратегия" для разделения логики обработки
 * различных интерактивных элементов интерфейса (кнопок). Каждый класс-реализация
 * отвечает за конкретный функциональный блок (например, выбор мастера, инфо о нас и т.д.).
 */
public interface CallBackHandler {

    /**
     * Определяет критерий применимости данного обработчика к входящему событию.
     * <p>
     * Как правило, проверка осуществляется по префиксу строки {@code callbackData}.
     * Например: {@code data.startsWith("staff_")}.
     *
     * @param data уникальная строка данных, привязанная к нажатой кнопке (callbackData).
     * @return {@code true}, если данный обработчик предназначен для обработки этой строки.
     */
    boolean supports(String data);

    /**
     * Инкапсулирует бизнес-логику обработки конкретного нажатия кнопки.
     * <p>
     * В этом методе происходит извлечение параметров из {@code callbackQuery.getData()},
     * обращение к сервисным слоям и формирование ответа пользователю через {@link MessageSender}.
     *
     * @param callbackQuery объект, содержащий метаданные нажатия (кто нажал, в каком сообщении, данные кнопки).
     * @param messageSender универсальный компонент для отправки сообщений, клавиатур и уведомлений.
     */
    void handle(CallbackQuery callbackQuery, MessageSender messageSender);
}