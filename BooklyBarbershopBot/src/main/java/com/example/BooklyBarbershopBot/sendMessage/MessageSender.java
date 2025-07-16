package com.example.BooklyBarbershopBot.sendMessage;

public interface MessageSender {
    void sendMessage(Long chatId, String text);

}
