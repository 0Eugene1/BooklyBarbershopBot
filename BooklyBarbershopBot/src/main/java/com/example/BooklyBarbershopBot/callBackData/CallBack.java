package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.BookDatesResponse;
import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.ParsingDtoService;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import com.example.BooklyBarbershopBot.telegramBot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallBack {

    @Setter
    private TelegramBot telegramBot;

    private final ParsingDtoService parsingDtoService;
    private final BarbershopService barbershopService;
    private final YclientsService yclientsService;


    @Value("${yclients.company-id}")
    private String companyId;

    public void handelCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        log.info("Chat ID: {}", chatId);

        if (chatId < 0) {
            log.warn("Попытка отправки в группу или канал. chatId: {}", chatId);
            return;
        }

        if (data.startsWith("book_")) {
            // твой текущий код для book_
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
                            button.setCallbackData("staff_" + staff.getId() + "_" + slug); // staffId и slug
                            rows.add(Collections.singletonList(button));
                        }
                    }

                    keyboard.setKeyboard(rows);

                    SendMessage message = SendMessage.builder().chatId(chatId.toString()).text("💈 Выберите мастера:").replyMarkup(keyboard).build();

                    telegramBot.execute(message);
                } catch (Exception e) {
                    log.error("Ошибка при получении мастеров для slug: {}", slug, e);
                    sendMessage(chatId, "❌ Не удалось получить список мастеров. Попробуйте позже.");
                }
            }, () -> sendMessage(chatId, "❌ Барбершоп не найден."));
        } else if (data.startsWith("staff_")) {
            // новый блок для обработки нажатия на мастера
            try {
                String[] parts = data.split("_", 3);
                if (parts.length < 3) {
                    sendMessage(chatId, "⚠️ Ошибка в данных callback.");
                    return;
                }
                String staffId = parts[1];
                String slug = parts[2];

                barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                    String companyId = barbershop.getYclientsCompanyId();
                    try {
                        List<ServiceDto> services = parsingDtoService.getServicesParsed(companyId);
                        //FIXME ВРМЕНЕЫЫЙ ЛОГ С ПРОВЕРКОЙ
                        log.info("Проверка staffId={} по всем услугам", staffId);
                        for (ServiceDto s : services) {
                            log.info("Услуга: {} (id={}), доступна для сотрудников: {}", s.getTitle(), s.getId(), s.getStaff());
                        }
//                        List<ServiceDto> staffServices = services.stream()
//                                .filter(s -> s.getStaff() != null && s.getStaff().contains(Long.parseLong(staffId)))
//                                .collect(Collectors.toList());

                        long staffIdLong = Long.parseLong(staffId);

                        List<ServiceDto> staffServices = services.stream()
                                .filter(s -> {
                                    List<StaffDto> staffList = s.getStaff();
                                    return staffList != null && staffList.stream()
                                            .anyMatch(staff -> staff.getId() == staffIdLong); // <-- вот здесь правильная проверка
                                })
                                .filter(s -> s.getTitle() != null && !s.getTitle().isBlank()) // фильтр по title
                                .collect(Collectors.toList());


                        if (staffServices.isEmpty()) {
                            sendMessage(chatId, "🔍 У этого мастера пока нет доступных услуг.");
                            return;
                        }

                        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                        for (ServiceDto service : staffServices) {
                            //FIXME ВРЕМЕННЫЙ ЛОГ
                            log.info("Услуга: {}, staff list: {}", service.getTitle(), service.getStaff());

                            InlineKeyboardButton button = new InlineKeyboardButton();
                            button.setText(service.getTitle() + (service.getPriceMin() != null ? " (от " + service.getPriceMin().intValue() + "₽)" : ""));
                            button.setCallbackData("service_" + service.getId() + "_" + staffId + "_" + slug);
                            //FIXME ВРЕМЕННЫЙ ЛОГ
                            log.info("Проверка staffId={} по всем услугам", staffId);

                            rows.add(Collections.singletonList(button));
                        }
                        keyboard.setKeyboard(rows);

                        SendMessage message = SendMessage.builder().chatId(chatId.toString()).text("💇 Услуги мастера:").replyMarkup(keyboard).build();

                        telegramBot.execute(message);
                    } catch (Exception e) {
                        log.error("Ошибка при получении услуг мастера", e);
                        sendMessage(chatId, "❌ Не удалось получить услуги мастера. Попробуйте позже.");
                    }
                }, () -> sendMessage(chatId, "❌ Барбершоп не найден."));
            } catch (Exception e) {
                log.error("Ошибка при обработке callback staff_", e);
                sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
            }
        } else if (data.startsWith("service_")) {
            try {
                String[] parts = data.split("_", 4);
                if (parts.length < 4) {
                    sendMessage(chatId, "⚠️ Ошибка в данных callback.");
                    return;
                }
                Long serviceId = Long.parseLong(parts[1]);
                Long staffId = Long.parseLong(parts[2]);
                String slug = parts[3];

                barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
                    String companyId = barbershop.getYclientsCompanyId();

                    try {
                        BookDatesResponse bookDates = yclientsService.getAvailableBookingDates(companyId, staffId, serviceId);

                        //FIXME ВРЕМЕНННЫЙ ЛОГ !
                        log.info("➡️ Запрос /book_dates с параметрами: companyId={}, staffId={}, serviceId={}", companyId, staffId, serviceId);


                        if (bookDates.getBookingDates() == null || bookDates.getBookingDates().isEmpty()) {
                            sendMessage(chatId, "📅 Нет доступных дат для записи.");
                            return;
                        }

                        // создаём кнопки с датами
                        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                        for (String date : bookDates.getBookingDates()) {
                            InlineKeyboardButton button = new InlineKeyboardButton();
                            button.setText(date);
                            button.setCallbackData("date_" + date + "_" + staffId + "_" + serviceId + "_" + slug);
                            rows.add(Collections.singletonList(button));
                        }

                        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                        keyboard.setKeyboard(rows);

                        SendMessage message = SendMessage.builder()
                                .chatId(chatId.toString())
                                .text("📅 Выберите дату:")
                                .replyMarkup(keyboard)
                                .build();

                        telegramBot.execute(message);
                    } catch (Exception e) {
                        log.error("Ошибка при получении дат", e);
                        sendMessage(chatId, "❌ Не удалось получить даты бронирования.");
                    }

                }, () -> sendMessage(chatId, "❌ Барбершоп не найден."));
            } catch (Exception e) {
                log.error("Ошибка обработки callback service_", e);
                sendMessage(chatId, "❌ Произошла ошибка.");
            }
    }else {
        sendMessage(chatId, "⚠️ Неизвестная команда.");
    }
}

    private void sendMessage(Long chatId, String text) {
        try {
            telegramBot.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения", e);
        }
    }
}

//            String rawResponse = yClientsService.getServices(companyId);
//            responseText = "📋 Услуги (сырые данные):\n\n" + rawResponse;
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
