package com.tailoredshapes.boobees;

import com.tailoredshapes.boobees.model.Prompt;
import com.tailoredshapes.boobees.repositories.DynamoMessageRepo;
import com.tailoredshapes.boobees.repositories.MessageRepo;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoMessageRepoTest {

    private DynamoDbClient dynamoDbClient;
    private MessageRepo messageRepo;

    @BeforeEach
    void setUp() {
        dynamoDbClient = Mockito.mock(DynamoDbClient.class);
        messageRepo = new DynamoMessageRepo("testTable", dynamoDbClient);
    }

    @Test
    void findLastNShouldReturnMessages() {
        QueryResponse response = QueryResponse.builder()
                .items(Arrays.asList(
                        itemMap("ASSISTANT", "Hello!"),
                        itemMap("USER", "Hi there!")
                ))
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        List<Prompt> result = messageRepo.findLastN(42L, 2);

        assertEquals(2, result.size());
        assertEquals("Hello!", result.get(0).prompt());
        assertEquals("Hi there!", result.get(1).prompt());
    }

    @Test
    void createAllShouldWriteMessages() {
        List<Prompt> chatPrompts = Arrays.asList(
                new Prompt(ChatMessageRole.USER.value(), "Test user content."),
                new Prompt(ChatMessageRole.ASSISTANT.value(), "Test assistant content.")
        );

        // You may choose to mock the dynamoDbClient method or test that your code behaves appropriately if an exception is thrown
        // In this case, you should write a try-catch inside the test and check that an exception is NOT thrown
        messageRepo.createAll(42L, chatPrompts);

        verify(dynamoDbClient).batchWriteItem(argThat((BatchWriteItemRequest b) ->{
            Map<String, List<WriteRequest>> items = b.requestItems();
            return items.containsKey("testTable") && items.get("testTable").size() == 2 && items.get("testTable").get(0).putRequest().item().get("chatId").n().equals("42");
        }));
    }


    // Helper method to create an item map for test
    private Map<String, AttributeValue> itemMap(String role, String content) {
        return Map.of(
                "message", AttributeValue.builder().m(Map.of(
                        "role", AttributeValue.builder().s(role).build(),
                        "content", AttributeValue.builder().s(content).build()
                )).build()
        );
    }
}
