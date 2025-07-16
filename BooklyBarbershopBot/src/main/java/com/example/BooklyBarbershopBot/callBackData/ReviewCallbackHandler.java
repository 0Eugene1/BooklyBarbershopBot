package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewCallbackHandler implements CallbackHandler {

    private final BarbershopService barbershopService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("feedback_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        log.info("Review callback data: {}", callbackQuery.getData());
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        String slug = data.substring("feedback_".length());

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String reviewsUrl = barbershop.getReviewsUrl();

            if (reviewsUrl == null || reviewsUrl.isEmpty()) {
                sendMessage(bot, chatId, "❌ Отзывы для этого барбершопа пока не доступны.");
                return;
            }

            //Отправляем пользователю ссылку на отзывы
            sendMessage(bot, chatId, "💬 Вот ссылка для оставления отзыва:\n" + reviewsUrl);

        }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));
    }

    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }

}

