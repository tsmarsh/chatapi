package com.tailoredshapes.boobees;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

public class TelegramRepo {

    static {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        AWSXRay.setGlobalRecorder(builder.build());
    }

    private final TelegramBot telegramBot;

    public TelegramRepo(String apiKey){
        telegramBot = new TelegramBot(apiKey);
    }

    private static final Logger LOG = LogManager.getLogger(TelegramRepo.class);

    boolean sendMessage(Long chatId, String text) {
        try(var ss = AWSXRay.beginSubsegment("Send Telegram Message")){
            SendMessage message = new SendMessage(chatId, text);
            try {
                telegramBot.execute(message);
                return true;
            } catch (Exception e) {
                LOG.error("Failed to send message to Telegram: " + message, e);
                ss.addException(e);
            }
        };
        return false;
    }

    public CompletableFuture<Boolean> sendMessageAsync(Long chatId, String message){
        return CompletableFuture.supplyAsync(() -> sendMessage(chatId, message));
    }

    void sendTyping(Long chatId) {
        SendChatAction sendChatAction = new SendChatAction(chatId, ChatAction.typing);
        try(var ss = AWSXRay.beginSubsegment("Send Telegram Typing")){
            try {
                telegramBot.execute(sendChatAction);
            } catch (Exception e) {
                LOG.error("Failed to send typing", e);
                ss.addException(e);
            }
        }

    }
}
