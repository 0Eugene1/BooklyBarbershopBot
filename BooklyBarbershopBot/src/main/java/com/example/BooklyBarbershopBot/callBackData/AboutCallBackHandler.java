package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Обработчик callback-запросов для предоставления информации о барбершопе.
 * <p>
 * Реагирует на нажатие кнопок с префиксом {@code about_}. Извлекает идентификатор
 * барбершопа (slug) из данных запроса и возвращает пользователю описание заведения.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AboutCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;

    /**
     * Определяет применимость обработчика к входящему запросу.
     *
     * @param data строка данных callback-запроса.
     * @return {@code true}, если строка начинается с префикса "about_".
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("about_");
    }

    /**
     * Обрабатывает логику нажатия кнопки "О нас".
     * <p>
     * 1. Извлекает slug путем удаления префикса из строки данных.<br>
     * 2. Запрашивает данные из сервиса {@link BarbershopService}.<br>
     * 3. Если текст описания отсутствует или пуст, отправляет заглушку по умолчанию.
     *
     * @param callbackQuery объект запроса от Telegram API.
     * @param messageSender компонент для отправки ответных сообщений пользователю.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        Long chatId = callbackQuery.getMessage().getChatId();
        // Извлекаем slug, отрезая префикс "about_"
        String slug = callbackQuery.getData().substring("about_".length());

        log.info("Обработка запроса 'О барбершопе' для slug: {}", slug);

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String about = barbershop.getAboutText();

            if (about == null || about.isBlank()) {
                about = "ℹ️ Информация о барбершопе пока не добавлена.";
            }

            messageSender.sendMessage(chatId, about);
        }, () -> {
            log.warn("Попытка получить информацию о несуществующем барбершопе: {}", slug);
            messageSender.sendMessage(chatId, "❌ Барбершоп не найден.");
        });
    }
}