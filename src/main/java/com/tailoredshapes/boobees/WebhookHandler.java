package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.request.SendChatAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;


public class WebhookHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(WebhookHandler.class);
	private static final TelegramBot TELEGRAM_BOT = new TelegramBot(System.getenv("TELEGRAM_BOT_TOKEN"));

	private final String queueUrl;
	private final AmazonSQS sqs;

	public WebhookHandler(){
		queueUrl = System.getenv("QUEUE_URL");
		LOG.info("QUEUE_URL: " + queueUrl);
		sqs = AmazonSQSClientBuilder.defaultClient();
	}

	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		String body = (String) input.get("body");
		Update update = BotUtils.parseUpdate(body);
		Chat chat = update.message().chat();
		String text = update.message().text();

		LOG.trace(String.format("Processing: %s for %d", text, chat.id()));

		try {
			SendMessageRequest send_msg_request = new SendMessageRequest()
					.withQueueUrl(queueUrl)
					.withMessageGroupId(chat.id().toString())
					.withMessageBody(body);

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
