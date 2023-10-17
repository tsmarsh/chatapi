package com.tailoredshapes.boobees.steps.audio;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailoredshapes.boobees.repositories.TelegramRepo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.tailoredshapes.underbar.ocho.UnderBar.map;

public record SQSAudioDelegate(TelegramRepo telegramRepo, AmazonPolly amazonPolly)  implements RequestHandler<SQSEvent, SQSBatchResponse> {


    private static final Logger LOG = LogManager.getLogger(SQSAudioHandler.class);
    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();


        List<CompletableFuture<Boolean>> calls = map(event.getRecords(), (record) -> {
            String body = record.getBody();

            ObjectMapper jsonMapper = new ObjectMapper();
            try {
                JsonNode root = jsonMapper.readTree(body);
                Long chatId = root.get("chatId").asLong();
                String answer = root.get("answer").asText();

                telegramRepo.sendRecording(chatId);

                SynthesizeSpeechRequest synthesizeSpeechRequest = new SynthesizeSpeechRequest()
                        .withEngine(Engine.Neural)
                        .withOutputFormat(OutputFormat.Mp3)
                        .withVoiceId(VoiceId.fromValue(System.getenv("VOICE")))
                        .withText(answer)
                        .withTextType(TextType.Text);
                SynthesizeSpeechResult synthesizeSpeechResult = amazonPolly.synthesizeSpeech(synthesizeSpeechRequest);

                return telegramRepo.sendAudioAsync(chatId, synthesizeSpeechResult.getAudioStream());

            } catch (Exception e) {
                LOG.error("Can't process payload", e);
            }

            var fail = new CompletableFuture<Boolean>();
            fail.complete(false);
            return fail;
        });

        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();

        return SQSBatchResponse.builder().withBatchItemFailures(batchItemFailures).build();
    }
}