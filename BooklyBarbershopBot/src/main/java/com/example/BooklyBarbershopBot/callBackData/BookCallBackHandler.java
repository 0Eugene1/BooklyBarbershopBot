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

/**
 * Обработчик callback-запросов с префиксом "book_", отвечающий за показ списка мастеров
 * для выбранного барбершопа и предоставляющий пользователю возможность выбрать мастера для записи.
 * <p>
 * Получает список доступных для записи мастеров из Yclients по компании и формирует клавиатуру с кнопками.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;

    /**
     * Проверяет, поддерживает ли данный обработчик callback с указанными данными.
     *
     * @param data данные callback-запроса
     * @return true, если данные начинаются с "book_"
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("book_");
    }

    /**
     * Обрабатывает callback-запрос, отправляя пользователю клавиатуру с мастерами,
     * доступными для записи в указанном барбершопе.
     *
     * @param callbackQuery объект callback-запроса от Telegram
     * @param bot           экземпляр Telegram-бота для отправки сообщений
     */
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

    /**
     * Вспомогательный метод для отправки текстового сообщения пользователю.
     *
     * @param bot    экземпляр Telegram-бота
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}
