package com.example.BooklyBarbershopBot.inlineButtons;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Компонент для создания inline-клавиатуры с основными кнопками меню.
 */
@Component
public class InlineKeyboard {

    /**
     * Создает inline-клавиатуру с кнопками для основного меню барбершопа.
     *
     * @param slug уникальный идентификатор барбершопа для callbackData
     * @return InlineKeyboardMarkup с кнопками: Записаться, О нас, Оставить отзыв, Отмена
     */
    public InlineKeyboardMarkup createMenuInlineKeyboard(String slug) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

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
                        .text("❌ Отмена записи")
                        .callbackData("cancel_menu")
                        .build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
    public InlineKeyboardMarkup startWithoutSlugKeyboard() {
        InlineKeyboardButton choose = InlineKeyboardButton.builder()
                .text("📍 Выбрать барбершоп")
                .url("https://t.me/BooklyHelp")
                .build();

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(choose)))
                .build();
    }
}
