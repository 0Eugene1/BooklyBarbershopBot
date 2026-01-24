package com.example.BooklyBarbershopBot.telegramBot.adminBot;

import com.example.BooklyBarbershopBot.callBackData.SetAdminCommandHandler;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Бот для администраторов сети барбершопов.
 * <p>
 * Служит для регистрации администраторов филиалов. При получении команды /setadmin
 * связывает chatId текущего пользователя с конкретным барбершопом (slug),
 * что позволяет в дальнейшем отправлять этому пользователю уведомления о записях и отзывы.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBot extends TelegramLongPollingBot {

    private final SetAdminCommandHandler setAdminCommandHandler;
    private final MessageSender messageSender;

    @Value("${telegram.adminbot.token}")
    private String token;

    @Value("${telegram.adminbot.username}")
    private String username;

    /**
     * Основной метод обработки входящих событий от Telegram.
     * <p>
     * На данный момент обрабатывает только текстовые сообщения,
     * фокусируясь на команде назначения администратора.
     */
    @Override
    public void onUpdateReceived(Update update) {
        // Игнорируем всё, кроме текстовых сообщений
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String text = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();

        // Обработка команды регистрации администратора: /setadmin <barbershop_slug>
        if (text.startsWith("/setadmin")) {
            String[] parts = text.split(" ", 2);

            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /setadmin <slug>\n\nПример: /setadmin central_barber");
                return;
            }

            String slug = parts[1].trim();
            log.info("Запрос на установку админа для слага: {} от chatId: {}", slug, chatId);

            setAdminCommandHandler.handle(chatId, slug);
            return;
        }

        log.debug("Админ-бот получил сообщение, которое не является командой: {}", text);
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    /**
     * Вспомогательный метод для отправки текстовых ответов.
     */
    private void sendMessage(Long chatId, String text) {
        messageSender.sendMessage(chatId, text);
    }
}