package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.LogManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SQSChatDelegateTest {

    private Assistant assistant;
    private TelegramBot telegramBot;
    private SQSChatDelegate sqsChatDelegate;

    @BeforeEach
    void setUp() {
        assistant = mock(Assistant.class);
        telegramBot = mock(TelegramBot.class);
        sqsChatDelegate = new SQSChatDelegate(assistant, telegramBot);
    }

    @Test
    void extractMessagesFromEventSuccessfully() {
        SQSEvent sqsEvent = mock(SQSEvent.class);
        SQSEvent.SQSMessage sqsMessage = mock(SQSEvent.SQSMessage.class);

        when(sqsEvent.getRecords()).thenReturn(List.of(sqsMessage));
        when(sqsMessage.getBody()).thenReturn(telegramPayload);

        Map<Long, List<String>> result = sqsChatDelegate.extractMessagesFromEvent(sqsEvent);

        assertEquals(1, result.size());
        assertEquals(1, result.get(54321L).size());
        assertEquals("Hello, how are you?", result.get(54321L).get(0));
    }

    @Test
    public void testHandleRequest() {
        // Mocking dependencies
        Assistant mockAssistant = mock(Assistant.class);
        TelegramBot mockTelegramBot = mock(TelegramBot.class);

        CompletableFuture<String> futureResponse = CompletableFuture.completedFuture("answer");
        when(mockAssistant.answerAsync(any(), any())).thenReturn(futureResponse);
        // Create an instance of the class under test
        SQSChatDelegate delegate = new SQSChatDelegate(mockAssistant, mockTelegramBot);

        // Create the input data
        Context mockContext = mock(Context.class);

        SQSEvent sqsEvent = mock(SQSEvent.class);
        SQSEvent.SQSMessage sqsMessage = mock(SQSEvent.SQSMessage.class);

        when(sqsEvent.getRecords()).thenReturn(List.of(sqsMessage));
        when(sqsMessage.getBody()).thenReturn(telegramPayload);

        Map<Long, List<String>> messages = new HashMap<>();
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        SQSBatchResponse response = delegate.handleRequest(sqsEvent, mockContext);

        assertEquals(0, response.getBatchItemFailures().size());
    }


    @Test
    void extractMessagesFromEventUnsuccessfully() {
        SQSEvent sqsEvent = mock(SQSEvent.class);
        SQSEvent.SQSMessage sqsMessage = mock(SQSEvent.SQSMessage.class);

        when(sqsEvent.getRecords()).thenReturn(List.of(sqsMessage));
        when(sqsMessage.getBody()).thenReturn("Not a telegram update");

        Map<Long, List<String>> result = sqsChatDelegate.extractMessagesFromEvent(sqsEvent);

        assertEquals(0, result.size());
    }

    @Test
    void processChatShouldInvokeAssistantAndSendTyping() {
        // Arrange
        Long chatId = 123L;
        List<String> msgs = List.of("question 1", "question 2");

        CompletableFuture<String> futureResponse = CompletableFuture.completedFuture("answer");
        when(assistant.answerAsync(msgs, chatId)).thenReturn(futureResponse);

        // Act
        sqsChatDelegate.processChat(chatId, msgs);

        // Assert
        verify(telegramBot).execute(any(SendChatAction.class)); // Verify that typing was sent
        verify(telegramBot).execute(any(SendMessage.class)); // Verify that the correct message was sent
    }

    @Test
    void processChatShouldHandleExceptionFromAssistant() {
        // Arrange
        Long chatId = 123L;
        List<String> msgs = List.of("question 1", "question 2");

        CompletableFuture<String> futureResponse = new CompletableFuture<>();
        futureResponse.completeExceptionally(new RuntimeException("An error occurred"));

        when(assistant.answerAsync(msgs, chatId)).thenReturn(futureResponse);

        // Act
        sqsChatDelegate.processChat(chatId, msgs);

        // Assert
        verify(telegramBot).execute(any(SendMessage.class));
    }

    @Test
    public void testProcessMessages() {
        // Mocking dependencies
        Assistant mockAssistant = mock(Assistant.class);
        TelegramBot mockTelegramBot = mock(TelegramBot.class);

        // Create an instance of the class under test
        SQSChatDelegate delegate = spy(new SQSChatDelegate(mockAssistant, mockTelegramBot));

        // Prepare the data
        Map<Long, List<String>> messages = new HashMap<>();
        messages.put(12345L, List.of("Message 1", "Message 2"));

        // Call the method under test
        delegate.processMessages(messages);

        // Verify interactions
        messages.forEach((chatId, msgs) -> {
            for (String msg : msgs) {
                verify(delegate).processChat(chatId, msgs);
            }
        });
    }


    private String telegramPayload = "{\n" +
            "  \"update_id\": 123456789,\n" +
            "  \"message\": {\n" +
            "    \"message_id\": 100,\n" +
            "    \"from\": {\n" +
            "      \"id\": 12345,\n" +
            "      \"first_name\": \"John\",\n" +
            "      \"last_name\": \"Doe\",\n" +
            "      \"username\": \"johndoe\",\n" +
            "      \"language_code\": \"en\"\n" +
            "    },\n" +
            "    \"chat\": {\n" +
            "      \"id\": 54321,\n" +
            "      \"type\": \"private\",\n" +
            "      \"title\": \"MyChat\",\n" +
            "      \"username\": \"my_chat\",\n" +
            "      \"first_name\": \"My\",\n" +
            "      \"last_name\": \"Chat\"\n" +
            "    },\n" +
            "    \"date\": 1609459200,\n" +
            "    \"text\": \"Hello, how are you?\"\n" +
            "  }\n" +
            "}\n";
}
