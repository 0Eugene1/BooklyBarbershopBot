package com.example.BooklyBarbershopBot.handlers;

import com.example.BooklyBarbershopBot.dto.BookingData;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.inlineButtons.InlineKeyboard;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.BookingStateService;
import com.example.BooklyBarbershopBot.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Обработчик команды вызова главного меню.
 * <p>
 * Класс отвечает за определение контекста (выбранного барбершопа) и отображение
 * основных функций бота: запись, контакты, информация о нас.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MenuCommandHandler {

    private final BarbershopService barbershopService;
    private final ClientService clientService;
    private final InlineKeyboard inlineKeyboard;

    /**
     * Формирует и отправляет главное меню.
     * <p>
     * Алгоритм поиска контекста:
     * 1. Проверка текущей сессии бронирования в {@link BookingStateService}.
     * 2. Если сессия пуста, поиск в профиле {@link Client} (последний посещенный slug).
     * 3. Если slug найден, загружаются данные из {@link BarbershopService}.
     *
     * @param chatId       ID пользователя.
     * @param bookingCache сервис хранения состояний записи.
     * @param sender       обработчик отправки сообщений.
     */
    public void handle(Long chatId, BookingStateService bookingCache, TelegramMessageSender sender) {
        String slug = resolveSlug(chatId, bookingCache);

        if (slug == null) {
            sender.sendMessage(chatId, "👋 Выберите барбершоп, чтобы начать заново.");
            return;
        }

        renderMenu(chatId, slug, sender);
    }

    /**
     * Пытается найти идентификатор барбершопа для текущего пользователя.
     */
    private String resolveSlug(Long chatId, BookingStateService bookingCache) {
        BookingData data = bookingCache.get(chatId);
        if (data != null && data.getSlug() != null) {
            return data.getSlug();
        }
        return clientService.findByTelegramId(chatId)
                .map(Client::getLastUsedSlug)
                .orElse(null);
    }

    /**
     * Отрисовывает интерфейс меню с кнопками.
     */
    private void renderMenu(Long chatId, String slug, TelegramMessageSender sender) {
        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String greeting = barbershop.getGreeting() != null ? barbershop.getGreeting() : "";
            String fullText = "🏪 <b>" + barbershop.getName() + "</b>\n\n" + greeting;

            SendMessage response = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(fullText)
                    .parseMode("HTML")
                    .replyMarkup(inlineKeyboard.createMenuInlineKeyboard(slug))
                    .build();

            try {
                sender.executeMessage(response);
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке меню для chatId {}: {}", chatId, e.getMessage());
            }
        }, () -> sender.sendMessage(chatId, "❌ Барбершоп не найден."));
    }

    /**
     * Внутренний интерфейс для инверсии зависимостей при отправке сообщений.
     */
    public interface TelegramMessageSender {
        void sendMessage(Long chatId, String text);
        void executeMessage(SendMessage message) throws TelegramApiException;
    }
}