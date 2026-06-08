package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик инициации сбора обратной связи (кнопка «Оставить отзыв»).
 * <p>
 * Показывает меню выбора рейтинга; дальнейшие шаги обрабатывает {@link RatingCallBackHandler}.
 */
@Component
@Order(20)
@Slf4j
public class ReviewCallBackHandler implements CallBackHandler {

    @Override
    public boolean supports(String data) {
        return data.startsWith("feedback_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        String slug = data.substring("feedback_".length());
        showRatingButtons(messageSender, chatId, slug);
    }

    private void showRatingButtons(MessageSender messageSender, Long chatId, String slug) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            StringBuilder stars = new StringBuilder();
            for (int j = 0; j < i; j++) {
                stars.append("⭐");
            }

            rows.add(List.of(
                    InlineKeyboardButton.builder()
                            .text(stars.toString())
                            .callbackData("rating_" + i + "_" + slug)
                            .build()
            ));
        }

        messageSender.sendMessage(
                chatId,
                "Пожалуйста, оцените барбершоп:",
                InlineKeyboardMarkup.builder().keyboard(rows).build()
        );
    }
}
