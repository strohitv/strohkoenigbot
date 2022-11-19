package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.*;

@RequiredArgsConstructor
public class S3CookieHandler extends CookieHandler {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final String gtoken;
	private final boolean addDnt;

	@Override
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
		Map<String, List<String>> requestHeadersCopy = new HashMap<>(requestHeaders);

		List<String> cookies = new ArrayList<>();
		cookies.add(String.format("_gtoken=%s", gtoken));

		if (addDnt) {
			cookies.add("_dnt=1");
		}

		requestHeadersCopy.put("Cookie", cookies);

		return Collections.unmodifiableMap(requestHeadersCopy);
	}

	@Override
	public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
//		logger.info(uri);
//		responseHeaders.forEach(logger::info);
	}
}
