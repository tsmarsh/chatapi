package com.tailoredshapes.boobees.steps.audio;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.AmazonPollyClientBuilder;
import com.amazonaws.services.polly.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailoredshapes.boobees.repositories.TelegramRepo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.tailoredshapes.underbar.ocho.UnderBar.map;


public class SQSAudioHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final SQSAudioDelegate sqsAudioDelegate;

    public SQSAudioHandler() {
        TelegramRepo telegramRepo = new TelegramRepo(System.getenv("TELEGRAM_BOT_TOKEN"));
        AmazonPolly amazonPolly = AmazonPollyClientBuilder.defaultClient();

        sqsAudioDelegate = new SQSAudioDelegate(telegramRepo, amazonPolly);
    }


    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        return sqsAudioDelegate.handleRequest(event, context);
    }

}
