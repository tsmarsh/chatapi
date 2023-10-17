package com.tailoredshapes.boobees;

import com.tailoredshapes.boobees.model.Prompt;
import com.tailoredshapes.boobees.repositories.Assistant;
import com.tailoredshapes.boobees.repositories.DynamoMessageRepo;
import com.tailoredshapes.boobees.repositories.MessageRepo;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AssistantTest {

    private OpenAiService openAIClient;
    private OpenAiService openAiService;

    private MessageRepo messageRepo;
    private Assistant assistant;

    @BeforeEach
    void setUp() {
        Dotenv dotenv = Dotenv.load();
        String apiKey = dotenv.get("OPENAI_API_KEY");

        openAIClient = Mockito.mock(OpenAiService.class);
        messageRepo = Mockito.mock(DynamoMessageRepo.class);

        openAiService = new OpenAiService(apiKey);

        String failMessage = "Sorry, I couldn't understand that.";
        assistant = new Assistant(openAIClient, failMessage, messageRepo, "Just be yourself");


    }

    @Test
    void answerShouldReturnValidResponse() {
        List<String> prompts = Arrays.asList("What's your name?", "Tell me a joke.");
        Long chatId = 42L;

        ChatCompletionResult chatCompletions = Mockito.mock(ChatCompletionResult.class);

        when(openAIClient.createChatCompletion(any())).thenReturn(chatCompletions);
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(new ChatMessage(ChatMessageRole.ASSISTANT.value(), "I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!"));

        when(chatCompletions.getChoices()).thenReturn(Arrays.asList(choice));
        when(messageRepo.findLastN(any(Long.class), any(Integer.class))).thenReturn(Collections.emptyList());

        String response = assistant.answer(prompts, chatId);

        assertEquals("I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!", response);
    }

    @Test
    void answerShouldReturnValidResponseIntegration() {
        List<String> prompts = Arrays.asList("What's your name?", "Tell me a joke.");
        Long chatId = 42L;

        when(messageRepo.findLastN(any(Long.class), any(Integer.class))).thenReturn(Collections.emptyList());

        Assistant ass = new Assistant(openAiService, "brb", messageRepo, "Just be yourself");
        String response = ass.answer(prompts, chatId);

        System.out.printf("Response: %s", response);
        assertNotEquals(response, "brb");
    }


    @Test
    void answerAsyncShouldReturnValidResponse() throws Exception{
        List<String> prompts = Arrays.asList("What's your name?", "Tell me a joke.");
        Long chatId = 42L;

        ChatCompletionResult chatCompletions = Mockito.mock(ChatCompletionResult.class);

        when(openAIClient.createChatCompletion(any())).thenReturn(chatCompletions);
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(new ChatMessage(ChatMessageRole.ASSISTANT.value(), "I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!"));

        when(chatCompletions.getChoices()).thenReturn(Arrays.asList(choice));
        when(messageRepo.findLastN(any(Long.class), any(Integer.class))).thenReturn(Collections.emptyList());


        String response = assistant.answerAsync(prompts, chatId).get();

        assertEquals("I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!", response);
    }

    @Test
    void answerAsyncShouldReturnValidResponseWithStoreMessages() throws Exception{
        List<String> prompts = Arrays.asList("What's your name?", "Tell me a joke.");
        Long chatId = 42L;

        ChatCompletionResult chatCompletions = Mockito.mock(ChatCompletionResult.class);

        when(openAIClient.createChatCompletion(any())).thenReturn(chatCompletions);
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(new ChatMessage(ChatMessageRole.ASSISTANT.value(), "I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!"));

        List<Prompt> storedPrompts = Arrays.asList(new Prompt("user", "Which way is up"), new Prompt("assistant", "Up is the opposite direction to the force of gravity."));
        when(chatCompletions.getChoices()).thenReturn(Arrays.asList(choice));
        when(messageRepo.findLastN(any(Long.class), any(Integer.class))).thenReturn(storedPrompts);


        String response = assistant.answerAsync(prompts, chatId).get();

        assertEquals("I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!", response);
    }


    @Test
    void embedShouldReturnAListOfVectorsFromOpenAI() throws Exception {
        List<Prompt> prompts = Arrays.asList(new Prompt(ChatMessageRole.USER.value(), "I am a prompt"), new Prompt(ChatMessageRole.USER.value(), "I am another prompt"));

        Assistant ass = new Assistant(openAiService, "brb", messageRepo, "Just be yourself");

        List<List<Double>> embed1 = ass.embed(prompts);

        assertEquals(embed1.size(), 2);
        assertNotNull(embed1.get(0));
        assertNotNull(embed1.get(1));
    }
}
