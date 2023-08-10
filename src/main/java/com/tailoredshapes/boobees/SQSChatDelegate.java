package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public record SQSChatDelegate(Assistant assistant,
                              TelegramBot telegramBot) implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Logger LOG = LogManager.getLogger(SQSChatHandler.class);

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        var messages = extractMessagesFromEvent(event);
        var fails = processMessages(messages);

        return SQSBatchResponse.builder().withBatchItemFailures(fails).build();
    }

    Map<Long, List<String>> extractMessagesFromEvent(SQSEvent event) {
        Map<Long, List<Update>> collect = event.getRecords().stream()
                .map(this::parseUpdate)
                .collect(
                        Collectors.groupingBy((Update u) -> u.message().chat().id()));

        Map<Long, List<String>> result = new HashMap<>();
        collect.forEach((ci, updates) -> result.put(ci, updates.stream().map(u -> u.message().text()).toList()));

        return result;
    }

    Update parseUpdate(SQSEvent.SQSMessage msg) {
        try {
            return BotUtils.parseUpdate(msg.getBody());
        } catch (Exception e) {
            LOG.error("Failed to parse telegram message", e);
            LOG.error("message: " + msg.getBody());
            return null; // or handle it according to your application's needs
        }
    }


    List<SQSBatchResponse.BatchItemFailure> processMessages(Map<Long, List<String>> messages) {
        List<SQSBatchResponse.BatchItemFailure> fails = new ArrayList<>();

        CompletableFuture.allOf(messages.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> processChat(entry.getKey(), entry.getValue()))).toArray(CompletableFuture[]::new)).join();

        return fails;
    }

    void processChat(Long chatId, List<String> msgs) {
        sendTyping(chatId);
        String message = "that is harder to answer than I was expecting";
        try {
            message = assistant.answerAsync(msgs, chatId).get(40, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error("Error generating chat response", e);
        } finally {
            sendMessage(chatId, message);
        }
    }

    void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        try {
            telegramBot.execute(message);
        } catch (Exception e) {
            LOG.error("Failed to send message to Telegram: " + message, e);
        }
    }

    void sendTyping(Long chatId) {
        SendChatAction sendChatAction = new SendChatAction(chatId, ChatAction.typing);
        try {
            telegramBot.execute(sendChatAction);
        } catch (Exception e) {
            LOG.error("Failed to send typing", e);
        }
    }
}

