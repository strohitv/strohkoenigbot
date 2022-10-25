package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class S3CookieHandler extends CookieHandler {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final String gtoken;

	@Override
	public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
		Map<String, List<String>> requestHeadersCopy = new HashMap<>(requestHeaders);
		requestHeadersCopy.put("Cookie", Collections.singletonList(String.format("_gtoken=%s", gtoken)));

		return Collections.unmodifiableMap(requestHeadersCopy);
	}

	@Override
	public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
//		logger.info(uri);
//		responseHeaders.forEach(logger::info);
	}
}
