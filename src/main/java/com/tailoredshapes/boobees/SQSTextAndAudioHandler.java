package com.tailoredshapes.boobees;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.AmazonPollyClientBuilder;
import com.amazonaws.services.polly.model.*;
import com.amazonaws.xray.AWSXRay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.tailoredshapes.underbar.ocho.UnderBar.map;


public class SQSTextAndAudioHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Logger LOG = LogManager.getLogger(SQSTextAndAudioHandler.class);

    private static TelegramRepo telegramRepo;
    private final AmazonPolly amazonPolly;


    public SQSTextAndAudioHandler() {
        telegramRepo = new TelegramRepo(System.getenv("TELEGRAM_BOT_TOKEN"));
        amazonPolly = AmazonPollyClientBuilder.defaultClient();
    }

    public static String markdownToSSML(String markdown) {
        Parser parser = Parser.builder().build();
        Document document = parser.parse(markdown);

        StringBuilder ssml = new StringBuilder("<speak><prosody rate=\"20%\">");

        NodeVisitor visitor = new NodeVisitor(
                new VisitHandler<>(com.vladsch.flexmark.ast.Paragraph.class, paragraph -> ssml.append("<p>").append(paragraph.getChars().unescape()).append("</p>"))
        );

        visitor.visit(document);

        ssml.append("</prosody></speak>");
        return ssml.toString();
    }

    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();


        try(var ss = AWSXRay.beginSubsegment("Telegramming")) {

            List<CompletableFuture<Boolean>> calls = map(event.getRecords(), (record) -> {
                String body = record.getBody();

                ObjectMapper jsonMapper = new ObjectMapper();
                try {
                    JsonNode root = jsonMapper.readTree(body);
                    Long chatId = root.get("chatId").asLong();
                    String answer = root.get("answer").asText();


                    if (answer.length() > 100) {
                        telegramRepo.sendRecording(chatId);

                        try {
                            SynthesizeSpeechRequest synthesizeSpeechRequest = new SynthesizeSpeechRequest()
                                    .withEngine(Engine.Neural)
                                    .withOutputFormat(OutputFormat.Mp3)
                                    .withVoiceId(VoiceId.Vicki)
                                    .withText(answer)
                                    .withTextType(TextType.Text);
                            SynthesizeSpeechResult synthesizeSpeechResult = amazonPolly.synthesizeSpeech(synthesizeSpeechRequest);

                            return telegramRepo.sendAudiMessageAsync(chatId, synthesizeSpeechResult.getAudioStream());
                        } catch (Exception e) {
                            ss.addException(e);
                            LOG.error("Audio Failure: " + e.getMessage(), e);
                            return telegramRepo.sendMessageAsync(chatId, answer);
                        }


                    } else {
                        return telegramRepo.sendMessageAsync(chatId, answer);
                    }

                } catch (Exception e) {
                    ss.addException(e);
                    LOG.error("Can't process payload: " + e.getMessage(), e);
                }
                var fail = new CompletableFuture<Boolean>();
                fail.complete(false);
                return fail;
            });

            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        }
        return SQSBatchResponse.builder().withBatchItemFailures(batchItemFailures).build();
    }

}
