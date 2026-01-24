package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;

/**
 * Центральный диспетчер (Router) callback-запросов от Telegram.
 * <p>
 * Класс реализует паттерн "Цепочка обязанностей", управляя коллекцией реализаций {@link CallBackHandler}.
 * Он отвечает за маршрутизацию входящих нажатий кнопок к соответствующим специализированным обработчикам
 * на основе содержимого {@code callbackData}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallBack {

    /**
     * Коллекция всех зарегистрированных обработчиков.
     * Spring автоматически внедряет сюда все бины, реализующие интерфейс {@link CallBackHandler}.
     */
    private final List<CallBackHandler> handlers;

    /**
     * Сервис для отправки ответных сообщений пользователю.
     */
    private final MessageSender messageSender;


    /**
     * Основной метод маршрутизации callback-запроса.
     * <p>
     * Итерирует по списку обработчиков и вызывает первый, чей метод {@code supports()} вернет {@code true}.
     * Обеспечивает глобальную обработку исключений (Exception Handling), чтобы сбой в одном
     * обработчике не привел к падению всего потока обработки обновлений.
     *
     * @param callbackQuery объект запроса, содержащий данные нажатой кнопки и контекст чата.
     */
    public void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        log.debug("Получен callback: data='{}' от chatId={}", data, chatId);

        for (CallBackHandler handler : handlers) {
            if (handler.supports(data)) {
                try {
                    handler.handle(callbackQuery, messageSender);
                } catch (Exception e) {
                    log.error(
                            "Критическая ошибка при обработке callbackData='{}' для chatId={}. Обработчик: {}",
                            data, chatId, handler.getClass().getSimpleName(), e
                    );
                    messageSender.sendMessage(chatId, "❌ Произошла ошибка при выполнении операции. Попробуйте позже.");
                }
                return;
            }
        }

        log.warn("Маршрут не найден: callbackData='{}' не поддерживается ни одним обработчиком.", data);
        messageSender.sendMessage(chatId, "⚠️ К сожалению, эта кнопка устарела или не поддерживается.");
    }
}