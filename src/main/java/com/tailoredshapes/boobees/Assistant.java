package com.tailoredshapes.boobees;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static com.tailoredshapes.underbar.ocho.UnderBar.map;

public class Assistant {

    private static final Logger LOG = LogManager.getLogger(Assistant.class);
    private final OpenAIClient openAIClient;
    //private final MessageRepo repo;

    private final ChatMessage systemPrompt;

    public Assistant(String openApiKey, String personality) {
        LOG.info("Initializing Assistant with key: " + openApiKey);

        this.openAIClient = new OpenAIClientBuilder().credential(new NonAzureOpenAIKeyCredential(openApiKey)).buildClient();

        systemPrompt = new ChatMessage(ChatRole.SYSTEM).setContent(personality);
    }

    public String answer(List<String> prompts, Long chatId) {
        List<ChatMessage> aiPrompts = list(systemPrompt);
        aiPrompts.addAll(map(prompts, (m) -> new ChatMessage(ChatRole.USER).setContent(m)));

        ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(aiPrompts);
        ChatCompletions chatCompletions = openAIClient.getChatCompletions("gpt-3.5-turbo", chatCompletionsOptions);
        return chatCompletions.getChoices().get(0).getMessage().getContent();
    }
}
