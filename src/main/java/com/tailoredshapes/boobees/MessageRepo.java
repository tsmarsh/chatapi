package com.tailoredshapes.boobees;

import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class MessageRepo {
    private final String tableName;
    private final DynamoDbClient dynamoDb;

    private static final Logger LOG = LogManager.getLogger(MessageRepo.class);

    public MessageRepo(String tableName, DynamoDbClient dynamoDb) {
        this.tableName = tableName;
        this.dynamoDb = dynamoDb;
    }

    public void create(Long chatId, ChatMessage message) {
        HashMap<String, AttributeValue> messageMap = new HashMap<>();
        messageMap.put("role", AttributeValue.builder().s(message.getRole().toString()).build());
        messageMap.put("content", AttributeValue.builder().s(message.getContent()).build());

        HashMap<String, AttributeValue> itemValues = new HashMap<>();
        itemValues.put("messageId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        itemValues.put("timestamp", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());
        itemValues.put("chatId", AttributeValue.builder().n(chatId.toString()).build());
        itemValues.put("message", AttributeValue.builder().m(messageMap).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(itemValues)
                .build();

        dynamoDb.putItem(request);
    }

    public List<ChatMessage> findLastN(Long chatId, int n) {
        QueryRequest request = QueryRequest.builder()
                .tableName(this.tableName)
                .indexName("ConversationIndex")
                .keyConditionExpression("chatId = :chatId")
                .expressionAttributeValues(Map.of(":chatId", AttributeValue.builder().n(chatId.toString()).build()))
                .limit(n)
                .scanIndexForward(false)
                .build();

        List<ChatMessage> result;
        try {
            var response = this.dynamoDb.query(request);
            result = response.items().stream()
                    .map(item -> convertToChatMessage(item.get("message").m()))
                    .collect(Collectors.toList());
        } catch (Exception error) {
            LOG.error(String.format("Error querying for chatId %s", chatId), error);
            result = new ArrayList<>();
        }

        return result;
    }

    private ChatMessage convertToChatMessage(Map<String, AttributeValue> messageMap) {
        var cm = new ChatMessage(ChatRole.fromString(messageMap.get("role").s()));
        cm.setContent( messageMap.get("content").s());
        return cm;
    }


    public void createAll(Long chatId, List<ChatMessage> chatPrompts) {
        List<WriteRequest> writeRequests = chatPrompts.stream()
                .map(chatMessage -> {
                    HashMap<String, AttributeValue> messageMap = new HashMap<>();
                    messageMap.put("role", AttributeValue.builder().s(chatMessage.getRole().toString()).build());
                    messageMap.put("content", AttributeValue.builder().s(chatMessage.getContent()).build());

                    HashMap<String, AttributeValue> itemValues = new HashMap<>();
                    itemValues.put("messageId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
                    itemValues.put("timestamp", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());
                    itemValues.put("chatId", AttributeValue.builder().n(chatId.toString()).build());
                    itemValues.put("message", AttributeValue.builder().m(messageMap).build());

                    PutRequest putRequest = PutRequest.builder().item(itemValues).build();
                    return WriteRequest.builder().putRequest(putRequest).build();
                })
                .toList();

        BatchWriteItemRequest batchWriteItemRequest = BatchWriteItemRequest.builder()
                .requestItems(Map.of(tableName, writeRequests))
                .build();

        try {
            dynamoDb.batchWriteItem(batchWriteItemRequest);
        } catch (Exception e) {
            LOG.error(String.format("Error batch writing items for chatId %s", chatId), e);
        }
    }

}
