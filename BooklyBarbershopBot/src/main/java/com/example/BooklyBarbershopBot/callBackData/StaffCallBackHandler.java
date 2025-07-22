package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.ServiceDto;
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
 * Обработчик callback-запросов для выбора мастера.
 * <p>
 * Поддерживает callbackData, начинающиеся с "staff_".
 * Загружает услуги, доступные выбранному мастеру, и формирует клавиатуру
 * с кнопками услуг для дальнейшего выбора.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;

    /**
     * Проверяет, начинается ли callbackData с "staff_".
     *
     * @param data данные callback
     * @return true, если поддерживается обработка выбора мастера
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("staff_");
    }

    /**
     * Обрабатывает callbackQuery, извлекает ID мастера и slug барбершопа,
     * получает список услуг мастера и отправляет пользователю клавиатуру с услугами.
     *
     * @param callbackQuery объект callbackQuery
     * @param bot экземпляр TelegramBot
     */
    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            String[] parts = data.split("_", 3);
            if (parts.length < 3) {
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            String staffIdStr = parts[1];
            String slug = parts[2];

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();
                try {
                    List<ServiceDto> services = parsingDtoService.getServicesParsed(companyId);
                    long staffId = Long.parseLong(staffIdStr);

                    List<ServiceDto> staffServices = services.stream()
                            .filter(s -> {
                                List<StaffDto> staffList = s.getStaff();
                                return staffList != null && staffList.stream().anyMatch(staff -> staff.getId() == staffId);
                            }).toList();

                    if (staffServices.isEmpty()) {
                        sendMessage(bot, chatId, "🔍 У этого мастера пока нет доступных услуг.");
                        return;
                    }

                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    for (ServiceDto service : staffServices) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(service.getTitle() +
                                (service.getPriceMin() != null ? " (от " + service.getPriceMin().intValue() + "₽)" : ""));
                        button.setCallbackData("service_" + service.getId() + "_" + staffIdStr + "_" + slug);
                        rows.add(Collections.singletonList(button));
                    }

                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                    keyboard.setKeyboard(rows);

                    SendMessage message = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("💇 Услуги мастера:")
                            .replyMarkup(keyboard)
                            .build();

                    bot.execute(message);
                } catch (Exception e) {
                    log.error("Ошибка при получении услуг мастера", e);
                    sendMessage(bot, chatId, "❌ Не удалось получить услуги мастера. Попробуйте позже.");
                }
            }, () -> sendMessage(bot, chatId, "❌ Барбершоп не найден."));

        } catch (Exception e) {
            log.error("Ошибка при обработке callback staff_", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    /**
     * Вспомогательный метод для отправки сообщения пользователю.
     *
     * @param bot экземпляр TelegramBot
     * @param chatId идентификатор чата
     * @param text текст сообщения
     */
    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }

}
