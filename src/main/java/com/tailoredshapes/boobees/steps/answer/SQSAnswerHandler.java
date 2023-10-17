package com.tailoredshapes.boobees.steps.answer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import com.tailoredshapes.boobees.repositories.Assistant;
import com.tailoredshapes.boobees.repositories.DynamoMessageRepo;
import com.theokanning.openai.service.OpenAiService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;


public class SQSAnswerHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    static {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        AWSXRay.setGlobalRecorder(builder.build());
    }

    private static final Logger LOG = LogManager.getLogger(SQSAnswerHandler.class);
    private final SQSAnswerDelegate sqsAnswerDelegate;

    public SQSAnswerHandler() {
        var openaiApiKey = System.getenv("OPENAI_API_KEY");
        var queueUrl = System.getenv("QUEUE_URL");

        var dynamoDb = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder().addExecutionInterceptor(new TracingInterceptor()).build())
                .build();

        var sqs = SqsClient.builder().overrideConfiguration(ClientOverrideConfiguration.builder().addExecutionInterceptor(new TracingInterceptor()).build()).build();

        var repo = new DynamoMessageRepo(System.getenv("TABLE_NAME"), dynamoDb);
        try {
            var openAIClient = new OpenAiService(openaiApiKey);
            var assistant = new Assistant(openAIClient, "brb", repo, System.getenv("SYSTEM_PROMPT"));
            sqsAnswerDelegate = new SQSAnswerDelegate(assistant, sqs, queueUrl);
        } catch (Exception e) {
            LOG.fatal("Cannot create OpenAI client", e);
            throw e;
        }
    }


    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        return sqsAnswerDelegate.handleRequest(event,context);
    }

}
