package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.handlers.MyBookingsHandler;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Обработчик callback-запроса для вызова меню управления записями (отмены).
 * <p>
 * Служит мостом между нажатием кнопки "Отмена" в главном меню и логикой
 * вывода списка бронирований. Проверяет регистрацию пользователя и делегирует
 * отрисовку списка компоненту {@link MyBookingsHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelMenuCallBackHandler implements CallBackHandler {

    private final MyBookingsHandler myBookingsHandler;
    private final ClientService clientService;

    @Override
    public boolean supports(String data) {
        return "cancel_menu".equals(data);
    }

    /**
     * Обрабатывает переход в меню "Мои записи".
     * <p>
     * Создает адаптер для {@link MyBookingsHandler.TelegramMessageSender},
     * чтобы связать универсальный {@link MessageSender} с требованиями хендлера.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        Long chatId = callbackQuery.getMessage().getChatId();

        log.debug("Обработка вызова меню записей для chatId: {}", chatId);

        clientService.findByTelegramId(chatId).ifPresentOrElse(client -> {
            // Создаем адаптер для соответствия интерфейсу, который требует MyBookingsHandler
            MyBookingsHandler.TelegramMessageSender adapter = new MyBookingsHandler.TelegramMessageSender() {
                @Override
                public void sendMessage(Long chatId, String text) {
                    messageSender.sendMessage(chatId, text);
                }

                @Override
                public void executeMessage(SendMessage message) throws TelegramApiException {

                    messageSender.sendMarkdown(Long.valueOf(message.getChatId()), message.getText());
                }
            };

            // Вызываем логику отображения записей
            myBookingsHandler.handle(chatId, adapter);

        }, () -> {
            log.warn("Клиент не найден для chatId: {}", chatId);
            messageSender.sendMessage(chatId, "⚠️ Клиент не найден. Пожалуйста, сначала зарегистрируйтесь.");
        });
    }
}