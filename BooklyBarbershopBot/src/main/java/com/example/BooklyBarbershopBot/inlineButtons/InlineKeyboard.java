package com.example.BooklyBarbershopBot.inlineButtons;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class InlineKeyboard {

    public InlineKeyboardMarkup createMenuInlineKeyboard(String slug) {
        List<List<InlineKeyboardButton>>  rows = new ArrayList<>();

        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("📅 Записаться")
                        .callbackData("book_" + slug)
                        .build(),
                InlineKeyboardButton.builder()
                        .text("🧾 О нас")
                        .callbackData("about_" + slug)
                        .build()
        ));

        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("💬 Оставить отзыв")
                        .callbackData("feedback_" + slug)
                        .build()
        ));

        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("❌ Отмена")
                        .callbackData("cancel_" + slug)
                        .build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}
