package com.tailoredshapes.boobees.steps.webhook;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.tailoredshapes.boobees.util.ApiGatewayResponse;
import com.tailoredshapes.boobees.util.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

public record WebhookDelegate(String queueUrl, SqsClient sqs) implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private static final Logger LOG = LogManager.getLogger(WebhookDelegate.class);

    @Override
    public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
        String body = (String) input.get("body");
        Update update = BotUtils.parseUpdate(body);
        Chat chat = update.message().chat();
        String text = update.message().text();

        LOG.trace(String.format("Processing: %s for %d", text, chat.id()));

        try {
            SendMessageRequest send_msg_request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageGroupId(chat.id().toString())
                    .messageBody(body)
                    .build();

            sqs.sendMessage(send_msg_request);
        } catch(Exception e){
            LOG.error("Error putting message on queue", e);
        }


        Response responseBody = new Response("{\"success\": true}", input);

        return ApiGatewayResponse.builder()
                .setStatusCode(200)
                .setObjectBody(responseBody)
                .build();
    }
}
