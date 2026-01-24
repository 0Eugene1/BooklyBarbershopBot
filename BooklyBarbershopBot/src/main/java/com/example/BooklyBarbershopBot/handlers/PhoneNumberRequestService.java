package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.sendMessage.TelegramMessageSender;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

/**
 * Сервис для запроса контактного номера телефона пользователя.
 * <p>
 * Использует механизм ReplyKeyboardMarkup с кнопкой специального типа
 * {@code request_contact}, которая позволяет пользователю отправить свой
 * подтвержденный номер телефона одним нажатием.
 */
@Service
public class PhoneNumberRequestService {

    /**
     * Отправляет сообщение с просьбой поделиться контактом.
     * <p>
     * Текст сообщения оформлен с использованием HTML-разметки для акцентирования
     * внимания на безопасности и простоте процесса.
     *
     * @param chatId идентификатор чата клиента.
     * @param sender интерфейс для взаимодействия с Telegram API.
     */
    public void sendRequestPhoneNumberKeyboard(Long chatId, TelegramMessageSender sender) {
        ReplyKeyboardMarkup markup = getReplyKeyboardMarkup();

        String text = "Спасибо! 😊\n\n" +
                "<b>Остался последний шаг</b> — поделитесь своим номером телефона.\n" +
                "Нажмите кнопку ниже 👇\n" +
                "Telegram сам отправит ваш номер — это безопасно и быстро!\n\n" +
                "Писать номер вручную <b>не нужно</b>.";

        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .replyMarkup(markup)
                .build();

        try {
            sender.executeMessage(msg);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при отправке клавиатуры для номера телефона для chatId: " + chatId, e);
        }
    }

    /**
     * Создает конфигурацию Reply-клавиатуры.
     * <p>
     * {@code setSelective(true)} гарантирует, что клавиатура будет показана
     * только пользователю, упомянутому в поле {@code reply_to_message_id}
     * или адресату сообщения (актуально для групповых чатов).
     */
    private static ReplyKeyboardMarkup getReplyKeyboardMarkup() {
        KeyboardButton contactButton = new KeyboardButton("📱 Поделиться номером телефона");
        contactButton.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(contactButton);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        markup.setSelective(true);
        return markup;
    }
}