package com.tailoredshapes.boobees.steps.webhook;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import com.tailoredshapes.boobees.util.ApiGatewayResponse;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Map;

import com.amazonaws.xray.AWSXRayRecorderBuilder;

public class WebhookHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	static {
		AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
		AWSXRay.setGlobalRecorder(builder.build());
	}

	private final WebhookDelegate delegate;

	public WebhookHandler(){

		String queueUrl = System.getenv("QUEUE_URL");
		SqsClient sqs = SqsClient.builder()
				.overrideConfiguration(ClientOverrideConfiguration.builder().addExecutionInterceptor(new TracingInterceptor()).build())
				.build();

		delegate = new WebhookDelegate(queueUrl, sqs);

	}

	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		return delegate.handleRequest(input, context);
	}
}
