package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.ParsingDtoService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookCallbackHandler implements CallbackHandler {

    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("book_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        String slug = data.replace("book_", "");

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String companyId = barbershop.getYclientsCompanyId();
            try {
                List<StaffDto> staffList = parsingDtoService.getStaffParsed(companyId);

                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                for (StaffDto staff : staffList) {
                    if (Boolean.TRUE.equals(staff.getBookable())) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(staff.getName());
                        button.setCallbackData("staff_" + staff.getId() + "_" + slug);
                        rows.add(Collections.singletonList(button));
                    }
                }

                keyboard.setKeyboard(rows);

                SendMessage message = SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("💈 Выберите мастера:")
                        .replyMarkup(keyboard)
                        .build();

                bot.execute(message);
            } catch (Exception e) {
                log.error("Ошибка при получении мастеров для slug: {}", slug, e);
                sendMessage(bot, chatId, "❌ Не удалось получить список мастеров. Попробуйте позже.");
            }
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
