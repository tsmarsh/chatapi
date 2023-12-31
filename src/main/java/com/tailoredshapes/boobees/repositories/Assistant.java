package com.tailoredshapes.boobees.repositories;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailoredshapes.boobees.model.Prompt;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.tailoredshapes.underbar.ocho.UnderBar.map;

public class Assistant {

    public static final String MODEL = System.getenv("MODEL") != null ? System.getenv("MODEL") : "gpt-3.5-turbo";

    static {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        AWSXRay.setGlobalRecorder(builder.build());
    }

    private static final Logger LOG = LogManager.getLogger(Assistant.class);
    private final OpenAiService openAIClient;

    private final String systemPrompt;

    public final String failMessage;
    private final MessageRepo repo;

    public Assistant(OpenAiService openAIClient, String failMessage, MessageRepo repo, String systemPrompt) {
        this.repo = repo;
        this.openAIClient = openAIClient;
        this.systemPrompt = systemPrompt;

        this.failMessage = failMessage;
        LOG.info("Using: %s".formatted(MODEL));
    }

    public String answer(List<String> prompts, Long chatId) {

        ChatMessage systemPrompt = new ChatMessage(ChatMessageRole.SYSTEM.value(), this.systemPrompt);
        ChatMessage formatPrompt = new ChatMessage(ChatMessageRole.SYSTEM.value(), "Please use markdown for formatting and emphasis, feel free to use emoji.");

        LOG.info("Using personality: %s".formatted(systemPrompt));

        List<Prompt> lastN = repo.findN(chatId, 30, prompts.get(prompts.size() - 1));

        Collections.reverse(lastN);

        LOG.info("Found %d items for context".formatted(lastN.size()));

        List<ChatMessage> chatPrompts = map(prompts, (m) -> new ChatMessage(ChatMessageRole.USER.value(), m));

        List<ChatMessage> aiPrompts = lastN.stream().map( (p) -> new ChatMessage(ChatMessageRole.valueOf(p.role().toUpperCase()).value(), p.prompt())).collect(Collectors.toList());
        aiPrompts.add(formatPrompt);
        aiPrompts.add(systemPrompt);
        aiPrompts.addAll(chatPrompts);

        try {
            LOG.debug("Prompts sent to AI: \n" + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(aiPrompts));
        } catch (JsonProcessingException e) {
            LOG.error("Can't display prompts", e);
        }

        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder().model(MODEL).messages(aiPrompts).build();

        String message = failMessage;

        try(var ss = AWSXRay.beginSubsegment("Calling OpenAI")){
            try {
                List<ChatCompletionChoice> choices = openAIClient.createChatCompletion(completionRequest).getChoices();
                if(choices.size() > 0){
                    ChatMessage answer = choices.get(0).getMessage();
                    message = answer.getContent();
                    chatPrompts.add(answer);
                }

                List<Prompt> ps = chatPrompts.stream().map((cm) -> new Prompt(cm.getRole(), cm.getContent())).toList();
                repo.createAll(chatId, ps);

            } catch (Exception e) {
                LOG.error("OpenAI is screwing around again", e);
                ss.addException(e);
            }

        }

        return message;
    }

    public CompletableFuture<String> answerAsync(List<String> prompts, Long chatId) {
        return CompletableFuture.supplyAsync(() -> answer(prompts, chatId));
    }

    public List<List<Double>> embed(List<Prompt> prompt) {
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder().model("text-embedding-ada-002").input(prompt.stream().map(Prompt::prompt).toList()).build();
        EmbeddingResult embeddings = openAIClient.createEmbeddings(embeddingRequest);
        List<Embedding> data = embeddings.getData();

        return data.stream().map(Embedding::getEmbedding).toList();
    }
}
