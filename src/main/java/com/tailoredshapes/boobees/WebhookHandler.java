package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.tailoredshapes.boobees.util.ApiGatewayResponse;
import com.tailoredshapes.boobees.util.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

import com.amazonaws.xray.AWSXRayRecorderBuilder;

public class WebhookHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	static {
		AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
		AWSXRay.setGlobalRecorder(builder.build());
	}

	private static final Logger LOG = LogManager.getLogger(WebhookHandler.class);

	private final String queueUrl;
	private final SqsClient sqs;

	public WebhookHandler(){
		queueUrl = System.getenv("QUEUE_URL");
		sqs = SqsClient.builder()
				.overrideConfiguration(ClientOverrideConfiguration.builder().addExecutionInterceptor(new TracingInterceptor()).build())
				.build();
	}

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
