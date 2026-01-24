package com.example.BooklyBarbershopBot.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Сущность Client — представляет клиента, который взаимодействует с ботом и может делать записи.
 * <p>
 * Хранит уникальный идентификатор в базе, Telegram ID пользователя, контактный телефон,
 * полное имя и email.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    /**
     * Уникальный идентификатор клиента в базе данных.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор клиента в Telegram.
     */
    private Long telegramId;

    /**
     * Уникальный номер телефона клиента.
     */
    @Column(unique = true)
    private String phone;

    /**
     * Полное имя клиента.
     */
    @Getter
    private String fullName;

    /**
     * Email клиента.
     */
    private String email;

    private String lastUsedSlug;
}