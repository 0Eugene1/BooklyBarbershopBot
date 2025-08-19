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

@Slf4j
@Component
@RequiredArgsConstructor
public class ChooseServiceCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("choose_service_");
    }

    @Override
    public void handle(CallbackQuery callbackQuery, TelegramBot bot) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        log.info("[ChooseService] Получен callback data='{}', chatId={}", data, chatId);

        try {
            String[] parts = data.split("_", 4);
            if (parts.length < 4) {
                log.warn("[ChooseService] Некорректный формат callback data, parts.length={}", parts.length);
                sendMessage(bot, chatId, "⚠️ Ошибка в данных callback.");
                return;
            }

            Long staffId;
            try {
                staffId = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                log.error("[ChooseService] Ошибка парсинга staffId из '{}'", parts[2], e);
                sendMessage(bot, chatId, "⚠️ Некорректный ID мастера.");
                return;
            }

            String slug = parts[3];
            log.info("[ChooseService] staffId={}, slug={}", staffId, slug);

            barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                String companyId = barbershop.getYclientsCompanyId();
                log.info("[ChooseService] Получен companyId={}", companyId);

                try {
                    List<ServiceDto> services = parsingDtoService.getServicesParsed(companyId);
                    log.info("[ChooseService] Всего услуг загружено: {}", services.size());

                    // Фильтруем услуги по мастеру с использованием equals
                    List<ServiceDto> staffServices = services.stream()
                            .filter(s -> {
                                List<StaffDto> staffList = s.getStaff();
                                boolean matched = staffList != null && staffList.stream()
                                        .anyMatch(staff -> staff.getId() != null && staff.getId().equals(staffId));
                                if (!matched) {
                                    log.debug("[ChooseService] Услуга '{}' не принадлежит мастеру с ID {}", s.getTitle(), staffId);
                                }
                                return matched;
                            })
                            .toList();

                    log.info("[ChooseService] Услуг после фильтрации по мастеру: {}", staffServices.size());

                    if (staffServices.isEmpty()) {
                        sendMessage(bot, chatId, "🔍 У этого мастера пока нет доступных услуг.");
                        return;
                    }
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    for (ServiceDto service : staffServices) {
                        String buttonText = service.getTitle() +
                                (service.getPriceMin() != null ? " (от " + service.getPriceMin().intValue() + "₽)" : "");

                        // Добавляем нулевой-width space в конец, чтобы Telegram не сжимал текст
                        buttonText += "\u200B";

                        InlineKeyboardButton button = InlineKeyboardButton.builder()
                                .text(buttonText)
                                .callbackData("service_" + service.getId() + "_" + staffId + "_" + slug)
                                .build();

                        rows.add(Collections.singletonList(button));
                    }

                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                    keyboard.setKeyboard(rows);

                    SendMessage message = SendMessage.builder()
                            .chatId(chatId.toString())
                            .text("\uD83D\uDC87 Выберите услугу:") // короткий заголовок
                            .replyMarkup(keyboard)
                            .build();

                    bot.execute(message);

                } catch (Exception e) {
                    log.error("[ChooseService] Ошибка при получении услуг мастера", e);
                    sendMessage(bot, chatId, "❌ Не удалось получить услуги мастера. Попробуйте позже.");
                }

            }, () -> {
                log.warn("[ChooseService] Барбершоп с слагом '{}' не найден", slug);
                sendMessage(bot, chatId, "❌ Барбершоп не найден.");
            });

        } catch (Exception e) {
            log.error("[ChooseService] Ошибка при обработке callback choose_service_", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    private void sendMessage(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("[ChooseService] Ошибка при отправке сообщения", e);
        }
    }
}