package com.tailoredshapes.boobees;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import com.tailoredshapes.stash.Stash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import static com.tailoredshapes.stash.Stash.stash;


public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(Handler.class);
	private static final TelegramBot TELEGRAM_BOT = new TelegramBot(System.getenv("TELEGRAM_BOT_TOKEN"));

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		Update update = BotUtils.parseUpdate((String) input.get("body"));

		LOG.info("Update: \n" + update.toString());
		LOG.info("Environment: \n" + new Stash(System.getenv()).toJSONString());
		LOG.info("Input \n" + new Stash(input).toJSONString());

		String openaiApiKey = System.getenv("OPENAI_API_KEY");
		String systemPrompt = System.getenv("SYSTEM_PROMPT");

		var assistant = new Assistant(openaiApiKey, systemPrompt);

		Chat chat = update.message().chat();
		String text = update.message().text();

		sendTyping(chat.id());

		String message = assistant.answer(text, chat.id());

		sendMessage(chat.id(), message);

		Response responseBody = new Response(stash("success", true).toJSONString(), input);

		return ApiGatewayResponse.builder()
				.setStatusCode(200)
				.setObjectBody(responseBody)
				.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless"))
				.build();
	}

	public void sendTyping(Long chatId){
		SendChatAction sendChatAction = new SendChatAction(chatId, ChatAction.typing);
		TELEGRAM_BOT.execute(sendChatAction);
	}
	public void sendMessage(Long chatId, String text){
		SendMessage message = new SendMessage(chatId, text);
		TELEGRAM_BOT.execute(message);
	}
}
