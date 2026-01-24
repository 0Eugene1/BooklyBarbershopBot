package com.example.BooklyBarbershopBot.callBackData;

import com.example.BooklyBarbershopBot.sendMessage.MessageSender;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Обработчик команды назначения администратора филиала.
 * <p>
 * Данный компонент связывает Telegram ID пользователя с записью о барбершопе в БД.
 * После успешного выполнения этого действия, указанный chatId начнет получать
 * системные уведомления (новые бронирования, отмены, отзывы) по данному филиалу.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SetAdminCommandHandler {

    private final BarbershopService barbershopService;
    private final MessageSender messageSender;

    /**
     * Назначает пользователя администратором указанного барбершопа.
     * <p>
     * Метод обновляет поле {@code adminChatId} в сущности барбершопа, найденной по слагу.
     * Подразумевается, что метод вызывается либо из защищенной текстовой команды,
     * либо через механизм авторизации.
     *
     * @param chatId идентификатор Telegram чата будущего администратора.
     * @param slug   уникальный строковый идентификатор барбершопа (филиала).
     */
    public void handle(Long chatId, String slug) {
        log.info("Попытка назначения администратора для филиала: {} (chatId: {})", slug, chatId);

        try {
            barbershopService.setAdminChatId(slug, chatId);

            messageSender.sendMessage(
                    chatId,
                    "✅ Вы назначены администратором барбершопа <code>" + slug + "</code>"
            );

            log.info("Пользователь {} успешно назначен администратором филиала {}", chatId, slug);
        } catch (Exception e) {
            log.error("Ошибка при назначении администратора для слага {}: {}", slug, e.getMessage());
            messageSender.sendMessage(chatId, "❌ Не удалось назначить администратора. Проверьте правильность ссылки или слага.");
        }
    }
}

