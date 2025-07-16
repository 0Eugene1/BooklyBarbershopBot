package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Component
@Slf4j
@RequiredArgsConstructor
public class AboutCallbackHandler implements CallbackHandler {

    private final BarbershopService barbershopService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("about_");
    }

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
