package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.NonAzureOpenAIKeyCredential;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;


public class SQSChatHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    static {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        AWSXRay.setGlobalRecorder(builder.build());
    }

    private static final Logger LOG = LogManager.getLogger(SQSChatHandler.class);
    private final SQSChatDelegate sqsChatDelegate;

    public SQSChatHandler() {
        var openaiApiKey = System.getenv("OPENAI_API_KEY");
        var systemPrompt = System.getenv("SYSTEM_PROMPT");
        var queueUrl = System.getenv("QUEUE_URL");

        var dynamoDb = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder().addExecutionInterceptor(new TracingInterceptor()).build())
                .build();

        var sqs = SqsClient.builder().overrideConfiguration(ClientOverrideConfiguration.builder().addExecutionInterceptor(new TracingInterceptor()).build()).build();

        var repo = new MessageRepo(System.getenv("TABLE_NAME"), dynamoDb);
        try {
            var openAIClient = new OpenAIClientBuilder().credential(new NonAzureOpenAIKeyCredential(openaiApiKey)).buildClient();
            var assistant = new Assistant(openAIClient, "brb", repo);
            sqsChatDelegate = new SQSChatDelegate(assistant, new TelegramRepo(System.getenv("TELEGRAM_BOT_TOKEN")), sqs, queueUrl);
        } catch (Exception e) {
            LOG.fatal("Cannot create OpenAI client", e);
            throw e;
        }
    }


    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        return sqsChatDelegate.handleRequest(event,context);
    }

}
