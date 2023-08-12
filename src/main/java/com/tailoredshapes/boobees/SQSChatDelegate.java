package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Update;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public record SQSChatDelegate(Assistant assistant,
                              TelegramRepo trepo, SqsClient sqs, String queueUrl) implements RequestHandler<SQSEvent, SQSBatchResponse> {

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
            LOG.debug("Message: %s".formatted(message));
        } catch (Exception e) {
            LOG.error("Error generating chat response", e);
        } finally {
            try {
                ObjectMapper jsonMapper = new ObjectMapper();
                ObjectNode root = jsonMapper.createObjectNode();
                root.put("answer", message);
                root.put("chatId", chatId);
                var payload = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);


                SendMessageRequest send_msg_request = SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageGroupId(chatId.toString())
                        .messageBody(payload).build();

                sqs.sendMessage(send_msg_request);
            } catch(Exception e){
                LOG.error("Error putting message on queue", e);
            }
        }
    }
}

