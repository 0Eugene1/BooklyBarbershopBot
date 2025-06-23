package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.service.ParsingDtoService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallBack {

    @Setter
    private TelegramBot telegramBot;

    private final YclientsService yClientsService;
    private final ParsingDtoService parsingDtoService;

    public void handelCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        log.info("Chat ID: {}", chatId);
        if (chatId < 0) {
            log.warn("Попытка отправки в группу или канал. chatId: {}", chatId);
            return;
        }
        String responseText;

        if (data.startsWith("book_")) {
            String companyId = "86034"; // ← вставь сюда актуальный ID
            try {
                //Получаем список объектов услуг
                List<ServiceDto> services = parsingDtoService.getServicesParsed(companyId);
                //Формируем удобочитаемый список
                StringBuilder sb = new StringBuilder();
                for (ServiceDto s : services) {
                    if (s.getActive() != null && s.getActive() == 1) {
                        sb.append("- ").append(s.getTitle());
                        if (s.getPriceMin() != null) {
                            //TODO ДОБАВИТЬ ОТ-ДО
                            sb.append(" (от ").append(s.getPriceMin().intValue()).append(" ₽)");
                        }
                        sb.append("\n");
                    }
                }
                responseText = sb.toString();
            } catch (Exception e) {
                log.error("Ошибка при парсинге услуг", e);
                responseText = "❌ Не удалось получить список услуг. Попробуйте позже.";

//            String rawResponse = yClientsService.getServices(companyId);
//            responseText = "📋 Услуги (сырые данные):\n\n" + rawResponse;
//        } else if (data.startsWith("about_")) {
//            responseText = "🛠 Раздел 'О нас' пока в разработке.";
//        } else if (data.startsWith("feedback_")) {
//            responseText = "🛠 Раздел отзывов пока в разработке.";
            }
        } else
            responseText = "⚠️ Неизвестная команда.";
        try {
            telegramBot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(responseText)
                    .build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}
