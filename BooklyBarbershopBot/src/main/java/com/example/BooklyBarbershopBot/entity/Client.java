package com.example.BooklyBarbershopBot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;

    @Column(unique = true)
    private String phone;


    @Getter
    private String fullName;

    private String email;

}
