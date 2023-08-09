package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SQSChatHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final String openaiApiKey;
    private final String systemPrompt;

    private final Assistant assistant;
    private final TelegramBot telegramBot;

    public SQSChatHandler(){
        openaiApiKey = System.getenv("OPENAI_API_KEY");
        systemPrompt = System.getenv("SYSTEM_PROMPT");

        assistant = new Assistant(openaiApiKey, systemPrompt);
        telegramBot = new TelegramBot(System.getenv("TELEGRAM_BOT_TOKEN"));
    }

    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        Map<Long, List<String>> messages = new HashMap<>(event.getRecords().size());

        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            String text = msg.getBody();

            Update update = BotUtils.parseUpdate(text);

            Chat chat = update.message().chat();
            String prompt = update.message().text();

            if(!messages.containsKey(chat.id())){
                messages.put(chat.id(), new ArrayList<>());
            }

            messages.get(chat.id()).add(prompt);
        }

        messages.forEach((chatId, msgs) -> {
            String message = assistant.answer(msgs, chatId);
            sendMessage(chatId, message);
        });
        return new SQSBatchResponse();
    }

    public void sendMessage(Long chatId, String text){
        SendMessage message = new SendMessage(chatId, text);
        telegramBot.execute(message);
    }
}
