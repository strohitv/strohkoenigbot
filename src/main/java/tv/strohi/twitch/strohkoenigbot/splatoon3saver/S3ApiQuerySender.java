package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.S3CookieHandler;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth.S3Authenticator;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.S3RequestSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth.S3Authenticator.SPLATOON3_WEBVIEWVERSION_CONFIG_NAME;

@Component
@RequiredArgsConstructor
public class S3ApiQuerySender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final S3RequestSender s3RequestSender;
	private final S3RequestKeyUtil requestKeyUtil;
	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;

	private final LogSender logSender;

	private final S3Authenticator authenticator;

	public String queryS3Api(Account account, S3RequestKey key) {
		return queryS3Api(account, key, null, null);
	}

	public String queryS3Api(Account account, S3RequestKey key, String additionalHeader, String additionalContent) {
		var actionHash = requestKeyUtil.load(key);
		logger.info("Sending request for hash '{}', additional header  '{}' and additional content '{}'", actionHash, additionalHeader, additionalContent);

		var variables = new HashMap<>(key.getAdditionalVars());
		if (additionalHeader != null) {
			variables.put(additionalHeader, additionalContent);
		}

		return doRequest(account.getGTokenSplatoon3(), account.getBulletTokenSplatoon3(), actionHash, variables);
	}

	public String queryS3ApiPaged(Account account, S3RequestKey key, String id, int page, int first, String cursor) {
		var actionHash = requestKeyUtil.load(key);

		logger.info("Sending request for hash '{}', id  '{}', page '{}', first '{}' and cursor '{}'", actionHash, id, page, first, cursor);

		var variables = new HashMap<>(key.getAdditionalVars());
		variables.put("cursor", cursor);
		variables.put("first", first);
		variables.put("page", page);
		variables.put("id", id);

		return doRequest(account.getGTokenSplatoon3(), account.getBulletTokenSplatoon3(), actionHash, variables);
	}

	private String doRequest(String gToken, String bulletToken, String actionHash, Map<String, Object> variables) {
		var requestObject = new S3RequestBody(variables, new Extensions(new PersistedQuery(actionHash)));

		var body = "";
		try {
			body = new ObjectMapper().writeValueAsString(requestObject);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

		return doRequest(gToken, bulletToken, body);
	}

	private String doRequest(String gToken, String bulletToken, String body) {
		String result;

		String webViewVersion = configurationRepository.findAllByConfigName(SPLATOON3_WEBVIEWVERSION_CONFIG_NAME).stream()
			.findFirst()
			.map(Configuration::getConfigValue)
			.orElse("");

		String address = "https://api.lp1.av5ja.srv.nintendo.net/api/graphql";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.uri(uri)
			.setHeader("Authorization", String.format("Bearer %s", bulletToken))
			.setHeader("Accept-Language", "en-US")
			.setHeader("Accept-Encoding", "gzip,deflate,br")
//			.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
			.setHeader("User-Agent", RequestSender.getDefaultUserAgent())
			.setHeader("X-Web-View-Ver", webViewVersion)
			.setHeader("Content-Type", "application/json")
			.setHeader("Accept", "*/*")
			.setHeader("Origin", "https://api.lp1.av5ja.srv.nintendo.net")
			.setHeader("X-Requested-With", "com.nintendo.znca")
			.setHeader("Referer", "https://api.lp1.av5ja.srv.nintendo.net/?lang=en-US&na_country=US&na_lang=en-US")
//				.setHeader("Accept-Encoding", "gzip, deflate")
			.build();

		var client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(120))
			.version(HttpClient.Version.HTTP_2)
			.cookieHandler(new S3CookieHandler(gToken, false))
			.build();

		result = s3RequestSender.sendRequestAndParseGzippedJson(client, request);

		if (result == null) {
			logSender.sendLogs(logger, "S3ApiQuerySender could not fulfill request.");
		}

		return result;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class S3RequestBody {
		public Map<String, Object> variables;
		public Extensions extensions;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Extensions {
		public PersistedQuery persistedQuery;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PersistedQuery {
		public String sha256Hash;
		public final Integer version = 1;
	}
}
