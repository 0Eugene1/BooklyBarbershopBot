package com.example.BooklyBarbershopBot.callBackData;

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

    private final List<CallbackHandler> handlers;
    @Setter
    private TelegramBot telegramBot;

    public void handelCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        for (CallbackHandler handler : handlers) {
            if (handler.supports(data)) {
                handler.handle(callbackQuery, telegramBot);
                return;
            }
        }
        sendMessage(callbackQuery.getMessage().getChatId(), "⚠️ Неизвестная команда.");
    }

    private void sendMessage(Long chatId, String text){
            try {
                telegramBot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения", e);
            }
        }
}


//        } else if (data.startsWith("about_")) {
//            responseText = "🛠 Раздел 'О нас' пока в разработке.";
//        } else if (data.startsWith("feedback_")) {
//            responseText = "🛠 Раздел отзывов пока в разработке.";


//TODO БУДУЩИЙ МЕТОДО ДЛЯ ПОЛУЧЕНИЯ КАТЕГОРИЙ УСЛУГ ПО CATEGORY_ID
//Map<Long, List<ServiceDto>> grouped = services.stream()
//                        .filter(s -> s.getActive() != null && s.getActive() == 1)
//                        .collect(Collectors.groupingBy(ServiceDto::getCategoryId));
//
//                StringBuilder sb = new StringBuilder("📋 Услуги по категориям:\n\n");
//
//                for (Map.Entry<Long, List<ServiceDto>> entry : grouped.entrySet()) {
//                    Long categoryId = entry.getKey();
//                    List<ServiceDto> serviceList = entry.getValue();
//
//                    String categoryName = yClientsService.getCategoryNameCached(companyId, categoryId.toString());
//
//                    sb.append("🗂 ").append(categoryName).append(":\n");
//
//                    for (ServiceDto s : serviceList) {
//                        sb.append("- ").append(s.getTitle());
//                        if (s.getPriceMin() != null) {
//                            sb.append(" (от ").append(s.getPriceMin().intValue()).append(" ₽)");
//                        }
//                        sb.append("\n");
//                    }
//                    sb.append("\n");
//                }
