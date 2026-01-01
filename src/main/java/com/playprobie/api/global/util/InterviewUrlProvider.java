package com.playprobie.api.global.util;

import java.util.UUID;

public class InterviewUrlProvider {

	private static final String STREAM_URL_PATTERN = "/interview/%s/stream";

	private InterviewUrlProvider() {}

	public static String getStreamUrl(UUID sessionUuid) {
		return String.format(STREAM_URL_PATTERN, sessionUuid.toString());
	}
}
