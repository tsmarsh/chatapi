package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
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


public class SQSChatHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final String openaiApiKey;
    private final String systemPrompt;

    private final Assistant assistant;
    private final TelegramBot telegramBot;

    private static final Logger LOG = LogManager.getLogger(SQSChatHandler.class);

    public SQSChatHandler(){
        openaiApiKey = System.getenv("OPENAI_API_KEY");
        systemPrompt = System.getenv("SYSTEM_PROMPT");

        assistant = new Assistant(openaiApiKey, systemPrompt, "brb");
        telegramBot = new TelegramBot(System.getenv("TELEGRAM_BOT_TOKEN"));
    }


    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        Map<Long, List<String>> messages = new HashMap<>(event.getRecords().size());
        List<SQSBatchResponse.BatchItemFailure> fails = new ArrayList<>();

        extractMessagesFromEvent(event, messages, fails);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        messages.forEach((chatId, msgs) -> {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    sendTyping(chatId);
                    String message = "that is harder to answer than I was expecting";
                    try {
                        CompletableFuture<String> completableAnswer = assistant.answerAsync(msgs, chatId);
                        message = completableAnswer.get(40, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        LOG.error("Error generating chat response", e);
                    } finally {
                        sendMessage(chatId, message);
                    }
                } catch (Exception e) {
                    LOG.error("Error generating chat response", e);
                }
            }));
        });

        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return SQSBatchResponse.builder().withBatchItemFailures(fails).build();
    }


    protected void generateResponses(Map<Long, List<String>> messages) {

    }

    protected void extractMessagesFromEvent(SQSEvent event, Map<Long, List<String>> messages, List<SQSBatchResponse.BatchItemFailure> fails) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            String text = msg.getBody();

            try {
                Update update = BotUtils.parseUpdate(text);
                Chat chat = update.message().chat();
                String prompt = update.message().text();

                if(!messages.containsKey(chat.id())){
                    messages.put(chat.id(), new ArrayList<>());
                }

                messages.get(chat.id()).add(prompt);
            } catch (Exception e){
                LOG.error("Failed to parse telegram message", e);
                LOG.error("message: " + text);
                fails.add(SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier(msg.getMessageId()).build());
            }
        }
    }

    public void sendMessage(Long chatId, String text){
        SendMessage message = new SendMessage(chatId, text);
        try {
            telegramBot.execute(message);
        }catch (Exception e){
            LOG.error("Failed to send message to Telegram: " + message, e);
        }
    }

    public void sendTyping(Long chatId){
        SendChatAction sendChatAction = new SendChatAction(chatId, ChatAction.typing);
        try {
            telegramBot.execute(sendChatAction);
        } catch (Exception e){
            LOG.error("Failed to send typing", e);
        }

    }
}
