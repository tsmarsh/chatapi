package com.tailoredshapes.boobees.steps.text;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import com.tailoredshapes.boobees.repositories.TelegramRepo;



public class SQSTextHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {


    private final SQSTextDelegate delegate;


    public SQSTextHandler() {

        TelegramRepo telegramRepo = new TelegramRepo(System.getenv("TELEGRAM_BOT_TOKEN"));

        delegate = new SQSTextDelegate(telegramRepo);
    }

    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        return delegate.handleRequest(event, context);
    }

}
