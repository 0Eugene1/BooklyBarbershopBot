package com.example.BooklyBarbershopBot.service.adminBotService;

import com.example.BooklyBarbershopBot.telegramBot.adminBot.AdminBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Сервис для отправки системных уведомлений администраторам через AdminBot.
 * <p>
 * Используется для оповещения персонала о новых бронированиях, отменах
 * и поступлении новых отзывов от клиентов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBotSender {

    /** Бин административного бота */
    private final AdminBot adminBot;

    /**
     * Отправляет сообщение администратору в формате HTML.
     * <p>
     * Метод автоматически применяет HTML-разметку, что позволяет использовать
     * теги &lt;b&gt;, &lt;i&gt; и &lt;code&gt; в текстах уведомлений для лучшей читаемости.
     *
     * @param chatId идентификатор чата администратора (или группы).
     * @param text   текст уведомления.
     */
    public void sendMessage(Long chatId, String text) {
        var message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();
        try {
            adminBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке уведомления администратору (ID: {}). Причина: {}",
                    chatId, e.getMessage());
        }
    }
}