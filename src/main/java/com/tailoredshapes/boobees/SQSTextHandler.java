package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.tailoredshapes.stash.Stash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class SQSTextHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Logger LOG = LogManager.getLogger(SQSTextHandler.class);

    private static TelegramRepo telegramRepo;

    public SQSTextHandler() {
        telegramRepo = new TelegramRepo(System.getenv("TELEGRAM_BOT_TOKEN"));
    }


    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();


        var calls = event.getRecords().stream().map((record) -> {
            String body = record.getBody();
            Stash stash = Stash.parseJSON(body);
            Long chatId = stash.get("chatId");
            String answer = stash.get("answer");

            return telegramRepo.sendMessageAsync(chatId, answer);

        }).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(calls).join();

        return SQSBatchResponse.builder().withBatchItemFailures(batchItemFailures).build();
    }

}
