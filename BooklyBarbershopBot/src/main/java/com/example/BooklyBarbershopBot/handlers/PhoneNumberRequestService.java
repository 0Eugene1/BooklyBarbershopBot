package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.sendMessage.TelegramMessageSender;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

@Service
public class PhoneNumberRequestService {

    public void sendRequestPhoneNumberKeyboard(Long chatId, TelegramMessageSender sender) {
        KeyboardButton contactButton = new KeyboardButton("📱 Отправить номер");
        contactButton.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(contactButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text("Спасибо! Теперь отправьте, пожалуйста, свой номер телефона.")
                .replyMarkup(markup)
                .build();

        try {
            sender.executeMessage(msg);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при отправке клавиатуры для номера телефона", e);
        }
    }
}
