package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.repository.BarbershopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для управления данными филиалов барбершопов.
 * <p>
 * Обеспечивает логику поиска заведений по их идентификаторам в Telegram (slug)
 * и управления контактными данными администраторов для оперативных уведомлений.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BarbershopService {

    /** Репозиторий для прямого взаимодействия с таблицей барбершопов. */
    private final BarbershopRepository barbershopRepository;

    /**
     * Выполняет поиск филиала по его текстовому идентификатору (slug).
     * <p>
     * Slug используется в стартовых ссылках бота (t.me/bot?start=slug)
     * для автоматического определения заведения.
     *
     * @param slug уникальный текстовый код барбершопа.
     * @return {@link Optional} с объектом филиала.
     */
    public Optional<Barbershop> getBySlug(String slug) {
        log.info("Поиск барбершопа по slug: {}", slug);
        Optional<Barbershop> barbershop = barbershopRepository.findByTelegramSlug(slug);
        if (barbershop.isEmpty()) {
            log.warn("Барбершоп не найден для slug: {}", slug);
        } else {
            log.info("Барбершоп найден: id={}, name={}", barbershop.get().getId(), barbershop.get().getName());
        }
        return barbershop;
    }

    /**
     * Привязывает идентификатор чата администратора к конкретному филиалу.
     *
     * @param slug   текстовый идентификатор филиала.
     * @param chatId Telegram ID администратора или группы.
     */
    public void setAdminChatId(String slug, Long chatId) {
        getBySlug(slug).ifPresent(barbershop -> {
            barbershop.setAdminChatId(chatId);
            barbershopRepository.save(barbershop);
            log.info("Администратор {} успешно привязан к филиалу {}", chatId, slug);
        });
    }

    /**
     * Получает Telegram ID администратора для отправки уведомлений.
     *
     * @param slug идентификатор филиала.
     * @return ID чата администратора или null, если заведение не найдено/админ не назначен.
     */
    public Long getAdminChatId(String slug) {
        return getBySlug(slug)
                .map(Barbershop::getAdminChatId)
                .orElse(null);
    }

    /**
     * Возвращает текстовый slug по внутреннему UUID барбершопа.
     * Полезно при формировании аналитических отчетов и логов.
     *
     * @param barbershopId внутренний UUID записи.
     * @return строковый slug или null, если запись отсутствует.
     */
    public String getSlugByBarbershopId(UUID barbershopId) {
        return barbershopRepository.findById(barbershopId)
                .map(Barbershop::getTelegramSlug)
                .orElse(null);
    }
}