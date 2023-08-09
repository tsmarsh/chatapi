package com.tailoredshapes.boobees;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

public class MessageRepo {

    private final AmazonDynamoDB amazonDynamoDB;

    public MessageRepo(String tableName) {
        amazonDynamoDB = AmazonDynamoDBClientBuilder.defaultClient();
    }

    public void create(Long chatId, String payload) {

    }
}
