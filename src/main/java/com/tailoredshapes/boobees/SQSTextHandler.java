package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.xray.AWSXRay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.tailoredshapes.underbar.ocho.UnderBar.map;


public class SQSTextHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Logger LOG = LogManager.getLogger(SQSTextHandler.class);

    private static TelegramRepo telegramRepo;


    public SQSTextHandler() {
        telegramRepo = new TelegramRepo(System.getenv("TELEGRAM_BOT_TOKEN"));
    }

    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();


        try (var ss = AWSXRay.beginSubsegment("Telegramming")) {

            List<CompletableFuture<Boolean>> calls = map(event.getRecords(), (record) -> {
                String body = record.getBody();

                ObjectMapper jsonMapper = new ObjectMapper();
                try {
                    JsonNode root = jsonMapper.readTree(body);
                    Long chatId = root.get("chatId").asLong();
                    String answer = root.get("answer").asText();

                    return telegramRepo.sendMessageAsync(chatId, answer);


                } catch (Exception e) {
                    ss.addException(e);
                    LOG.error("Can't process payload: " + e.getMessage(), e);
                }
                var fail = new CompletableFuture<Boolean>();
                fail.complete(false);
                return fail;
            });

            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        }
        return SQSBatchResponse.builder().withBatchItemFailures(batchItemFailures).build();
    }

}
