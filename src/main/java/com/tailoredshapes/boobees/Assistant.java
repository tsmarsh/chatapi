package com.tailoredshapes.boobees;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.tailoredshapes.underbar.ocho.UnderBar.list;

public class Assistant {

    private static final Logger LOG = LogManager.getLogger(Assistant.class);
    private final OpenAIClient openAIClient;
    //private final MessageRepo repo;

    private final ChatMessage systemPrompt;

    public Assistant(String openApiKey, String personality) {
        LOG.info("Initializing Assistant with key: " + openApiKey);

        this.openAIClient = new OpenAIClientBuilder().credential(new NonAzureOpenAIKeyCredential(openApiKey)).buildClient();
        //this.repo = repo;

        systemPrompt = new ChatMessage(ChatRole.SYSTEM).setContent(personality);
    }

    public String answer(String text, Long chatId) {
        ChatMessage prompt = new ChatMessage(ChatRole.USER).setContent(text);
        ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(list(systemPrompt, prompt));
        ChatCompletions chatCompletions = openAIClient.getChatCompletions("gpt-3.5-turbo", chatCompletionsOptions);
        return chatCompletions.getChoices().get(0).getMessage().getContent();
    }
}
