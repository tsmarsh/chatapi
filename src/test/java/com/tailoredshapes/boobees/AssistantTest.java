package com.tailoredshapes.boobees;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AssistantTest {

    private OpenAIClient openAIClient;
    private MessageRepo messageRepo;
    private Assistant assistant;

    @BeforeEach
    void setUp() {
        openAIClient = Mockito.mock(OpenAIClient.class);
        messageRepo = Mockito.mock(MessageRepo.class);

        String personality = "Friendly Assistant";
        String failMessage = "Sorry, I couldn't understand that.";
        assistant = new Assistant(openAIClient, failMessage, messageRepo);
    }

    @Test
    void answerShouldReturnValidResponse() {
        List<String> prompts = Arrays.asList("What's your name?", "Tell me a joke.");
        Long chatId = 42L;

        ChatCompletions chatCompletions = Mockito.mock(ChatCompletions.class);
        ChatMessage chatMessage = new ChatMessage(ChatRole.ASSISTANT).setContent("I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!");

        ChatChoice cc = Mockito.mock(ChatChoice.class);
        when(cc.getMessage()).thenReturn(chatMessage);

        when(chatCompletions.getChoices()).thenReturn(Collections.singletonList(cc));


        when(openAIClient.getChatCompletions(any(String.class), any())).thenReturn(chatCompletions);
        when(messageRepo.findLastN(any(Long.class), any(Integer.class))).thenReturn(Collections.emptyList());

        String response = assistant.answer(prompts, chatId);

        assertEquals("I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!", response);
    }

    @Test
    void answerAsyncShouldReturnValidResponse() throws Exception{
        List<String> prompts = Arrays.asList("What's your name?", "Tell me a joke.");
        Long chatId = 42L;

        ChatCompletions chatCompletions = Mockito.mock(ChatCompletions.class);
        ChatMessage chatMessage = new ChatMessage(ChatRole.ASSISTANT).setContent("I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!");

        ChatChoice cc = Mockito.mock(ChatChoice.class);
        when(cc.getMessage()).thenReturn(chatMessage);

        when(chatCompletions.getChoices()).thenReturn(Collections.singletonList(cc));


        when(openAIClient.getChatCompletions(any(String.class), any())).thenReturn(chatCompletions);
        when(messageRepo.findLastN(any(Long.class), any(Integer.class))).thenReturn(Collections.emptyList());

        String response = assistant.answerAsync(prompts, chatId).get();

        assertEquals("I'm GPT-3.5-turbo, and here's a joke: Why did the chicken cross the road? To get to the other side!", response);
    }
}
