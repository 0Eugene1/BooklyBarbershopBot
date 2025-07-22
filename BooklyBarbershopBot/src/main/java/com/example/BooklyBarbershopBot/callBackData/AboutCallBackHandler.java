package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Обработчик callback-запросов с префиксом "about_", отвечающий за отправку информации
 * о барбершопе пользователю.
 * Получает описание барбершопа по его уникальному идентификатору (slug) и отправляет его в чат.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AboutCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;

    /**
     * Проверяет, поддерживает ли данный обработчик callback с указанными данными.
     * @param data данные callback-запроса
     * @return true, если данные начинаются с "about_"
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("about_");
    }

    /**
     * Обрабатывает callback-запрос, отправляя информацию о барбершопе в чат.
     * @param callbackQuery объект callback-запроса от Telegram
     * @param bot экземпляр Telegram-бота для отправки сообщений
     */
    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String slug = callbackQuery.getData().substring("about_".length());
        log.info("About callback handler");

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String about = barbershop.getAboutText();
            if (about == null || about.isBlank()) {
                about = "ℹ️ Информация о барбершопе пока не добавлена.";
            }
            bot.sendMessage(chatId, about);
        }, () -> bot.sendMessage(chatId, "❌ Барбершоп не найден."));
    }
}
