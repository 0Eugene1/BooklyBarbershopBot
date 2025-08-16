package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.inlineButtons.InlineKeyboard;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MenuCommandHandler {

    private final BarbershopService barbershopService;
    private final ClientService clientService;
    private final InlineKeyboard inlineKeyboard;

    public void handle(Long chatId, Map<Long, BookingData> bookingCache, TelegramMessageSender sender) {
        String slug = null;

        // 1️⃣ Из кэша
        BookingData data = bookingCache.get(chatId);
        if (data != null && data.getSlug() != null) {
            slug = data.getSlug();
        }

        // 2️⃣ Из клиента
        if (slug == null) {
            slug = clientService.findByTelegramId(chatId)
                    .map(client -> client.getLastUsedSlug())
                    .orElse(null);
        }

        // 3️⃣ Если нет slug — просим выбрать
        if (slug == null) {
            sender.sendMessage(chatId, "👋 Выберите барбершоп, чтобы начать заново.");
            return;
        }

        final String finalSlug = slug;

        // 4️⃣ Отправка меню
        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String greeting = barbershop.getGreeting() != null ? barbershop.getGreeting() : "Добро пожаловать!";
            String fullText = "🏪 " + barbershop.getName() + "\n\n" + greeting;

            SendMessage response = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(fullText)
                    .replyMarkup(inlineKeyboard.createMenuInlineKeyboard(finalSlug))
                    .build();

            try {
                sender.executeMessage(response);
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке меню", e);
            }
        }, () -> sender.sendMessage(chatId, "❌ Барбершоп не найден."));
    }

    public interface TelegramMessageSender {
        void sendMessage(Long chatId, String text);

        void executeMessage(SendMessage message) throws TelegramApiException;
    }
}