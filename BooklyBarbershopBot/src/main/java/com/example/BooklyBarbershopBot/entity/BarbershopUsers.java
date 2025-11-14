package com.example.BooklyBarbershopBot.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "barbershop_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarbershopUsers {

    @EmbeddedId
    private BarbershopUsersId id;

}
