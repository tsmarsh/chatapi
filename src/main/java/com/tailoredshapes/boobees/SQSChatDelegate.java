package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Update;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tailoredshapes.stash.Stash.stash;


public record SQSChatDelegate(Assistant assistant,
                              TelegramRepo trepo, AmazonSQS sqs, String queueUrl) implements RequestHandler<SQSEvent, SQSBatchResponse> {

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
                        Collectors.groupingBy((Update u) -> u != null ? u.message().chat().id() : 0L));

        collect.remove(0L);
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
            return null;
        }
    }


    List<SQSBatchResponse.BatchItemFailure> processMessages(Map<Long, List<String>> messages) {
        List<SQSBatchResponse.BatchItemFailure> fails = new ArrayList<>();

        CompletableFuture.allOf(messages.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> processChat(entry.getKey(), entry.getValue()))).toArray(CompletableFuture[]::new)).join();

        return fails;
    }

    void processChat(Long chatId, List<String> msgs) {
        trepo.sendTyping(chatId);
        String message = "that is harder to answer than I was expecting";


        try {
            message = assistant.answerAsync(msgs, chatId).get(40, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error("Error generating chat response", e);
        } finally {
            try {
                SendMessageRequest send_msg_request = new SendMessageRequest()
                        .withQueueUrl(queueUrl)
                        .withMessageGroupId(chatId.toString())
                        .withMessageBody(stash("answer", message, "chatId", chatId).toJSONString());

                sqs.sendMessage(send_msg_request);
            } catch(Exception e){
                LOG.error("Error putting message on queue", e);
            }
        }
    }
}

