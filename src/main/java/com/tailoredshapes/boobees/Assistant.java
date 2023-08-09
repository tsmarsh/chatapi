package com.tailoredshapes.boobees;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static com.tailoredshapes.underbar.ocho.UnderBar.map;

public class Assistant {

    private static final Logger LOG = LogManager.getLogger(Assistant.class);
    private final OpenAIClient openAIClient;

    private final ChatMessage systemPrompt;
    public final String failMessage;

    public Assistant(String openApiKey, String personality, String failMessage) {
        LOG.info("Initializing Assistant with key: " + openApiKey);

        try {
            this.openAIClient = new OpenAIClientBuilder().credential(new NonAzureOpenAIKeyCredential(openApiKey)).buildClient();
        }catch (Exception e){
            LOG.fatal("Cannot create OpenAI client", e);
            throw e;
        }

        this.systemPrompt = new ChatMessage(ChatRole.SYSTEM).setContent(personality);
        this.failMessage = failMessage;
    }

    public String answer(List<String> prompts, Long chatId) {
        List<ChatMessage> aiPrompts = new ArrayList<>();
        aiPrompts.add(systemPrompt);

        aiPrompts.addAll(map(prompts, (m) -> new ChatMessage(ChatRole.USER).setContent(m)));

        ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(aiPrompts);
        chatCompletionsOptions.setMaxTokens(200);

        String message = failMessage;

        try {
            ChatCompletions chatCompletions = openAIClient.getChatCompletions("gpt-3.5-turbo", chatCompletionsOptions);
            message = chatCompletions.getChoices().get(0).getMessage().getContent();
        }catch (Exception e){
            LOG.error("OpenAI is screwing around again", e);
        }

        return message;
    }

    public CompletableFuture<String> answerAsync(List<String> prompts, Long chatId){
        return CompletableFuture.supplyAsync(() -> answer(prompts,chatId));
    }
}
