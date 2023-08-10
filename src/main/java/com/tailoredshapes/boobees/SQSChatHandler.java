package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.NonAzureOpenAIKeyCredential;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class SQSChatHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final String openaiApiKey;
    private final String systemPrompt;

    private final Assistant assistant;
    private final TelegramBot telegramBot;

    private static final Logger LOG = LogManager.getLogger(SQSChatHandler.class);
    private final SQSChatDelegate sqsChatDelegate;

    public SQSChatHandler() {
        openaiApiKey = System.getenv("OPENAI_API_KEY");
        systemPrompt = System.getenv("SYSTEM_PROMPT");

        var dynamoDb = DynamoDbClient.builder().region(Region.US_EAST_1).build();

        var repo = new MessageRepo(System.getenv("TABLE_NAME"), dynamoDb);
        try {
            var openAIClient = new OpenAIClientBuilder().credential(new NonAzureOpenAIKeyCredential(openaiApiKey)).buildClient();
            assistant = new Assistant(openAIClient, systemPrompt, "brb", repo);
        } catch (Exception e) {
            LOG.fatal("Cannot create OpenAI client", e);
            throw e;
        }
        telegramBot = new TelegramBot(System.getenv("TELEGRAM_BOT_TOKEN"));

        sqsChatDelegate = new SQSChatDelegate(assistant, telegramBot);
    }


    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        return sqsChatDelegate.handleRequest(event,context);
    }

}
