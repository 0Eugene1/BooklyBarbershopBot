package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.example.BooklyBarbershopBot.service.ParsingDtoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Обработчик callback-запросов для инициации процесса записи на стрижку.
 * <p>
 * При получении префикса {@code book_}, класс запрашивает через {@link ParsingDtoService}
 * список сотрудников из внешней системы (Yclients), фильтрует их по доступности
 * и формирует интерактивное меню выбора мастера.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookCallBackHandler implements CallBackHandler {

    private final BarbershopService barbershopService;
    private final ParsingDtoService parsingDtoService;

    /**
     * Проверяет, предназначен ли данный callback для отображения списка мастеров.
     *
     * @param data строка данных callback.
     * @return {@code true}, если данные начинаются с "book_".
     */
    @Override
    public boolean supports(String data) {
        return data.startsWith("book_");
    }

    /**
     * Обрабатывает выбор конкретного заведения для записи.
     * <p>
     * Алгоритм работы:
     * <ol>
     * <li>Извлекает идентификатор (slug) заведения.</li>
     * <li>Получает {@code companyId} для интеграции с Yclients.</li>
     * <li>Запрашивает список персонала и фильтрует мастеров с флагом {@code bookable}.</li>
     * <li>Формирует вертикальный список кнопок, отображая имя и специализацию мастера.</li>
     * <li>Передает управление следующему этапу через callback {@code staff_{id}_{slug}}.</li>
     * </ol>
     *
     * @param callbackQuery запрос от Telegram.
     * @param messageSender компонент отправки уведомлений.
     */
    @Override
    public void handle(CallbackQuery callbackQuery, MessageSender messageSender) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        String slug = data.replace("book_", "");

        log.debug("Запрос списка мастеров для барбершопа: {}", slug);

        barbershopService.getBySlug(slug).ifPresentOrElse(barbershop -> {
            String companyId = barbershop.getYclientsCompanyId();
            try {
                // Получение данных о сотрудниках из внешней системы
                List<StaffDto> staffList = parsingDtoService.getStaffParsed(companyId);
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                for (StaffDto staff : staffList) {
                    // Отображаем только тех мастеров, к которым открыта запись
                    if (Boolean.TRUE.equals(staff.getBookable())) {
                        String buttonText = staff.getName();

                        String specialization = staff.getSpecialization();
                        if (specialization != null && !specialization.isBlank()) {
                            buttonText += " (" + specialization + ")";
                        }

                        InlineKeyboardButton button = InlineKeyboardButton.builder()
                                .text(buttonText)
                                .callbackData("staff_" + staff.getId() + "_" + slug)
                                .build();
                        rows.add(Collections.singletonList(button));
                    }
                }

                if (rows.isEmpty()) {
                    log.warn("У компании {} (ID: {}) нет доступных для записи мастеров", slug, companyId);
                    messageSender.sendMessage(chatId, "⚠️ К сожалению, сейчас нет свободных мастеров для записи в этом филиале.");
                    return;
                }

                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboard(rows)
                        .build();

                messageSender.sendMessage(chatId, "💈 Выберите мастера:", keyboard);

            } catch (Exception e) {
                log.error("Сбой интеграции с Yclients для компании {}: {}", slug, e.getMessage());
                messageSender.sendMessage(chatId, "❌ Не удалось получить список мастеров. Пожалуйста, попробуйте позже или позвоните нам.");
            }
        }, () -> messageSender.sendMessage(chatId, "❌ Барбершоп не найден."));
    }
}