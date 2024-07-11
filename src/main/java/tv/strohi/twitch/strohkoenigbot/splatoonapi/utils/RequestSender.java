package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.DefaultUserAgentRetriever;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model.CookieRefreshException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

@Component
public class RequestSender {
	private static final String SPLATOON3_DEFAULT_USER_AGENT_CONFIG_NAME = "Splatoon3_DefaultUserAgent";
	private static String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36";
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	@Autowired
	public RequestSender(ConfigurationRepository configurationRepository, LogSender logSender) {
		var loadedUserAgent = new DefaultUserAgentRetriever().getDefaultUserAgent();

		if (loadedUserAgent != null) {
			DEFAULT_USER_AGENT = loadedUserAgent;

			Configuration defaultUserAgentConfigs = configurationRepository.findAllByConfigName(SPLATOON3_DEFAULT_USER_AGENT_CONFIG_NAME).stream()
				.findFirst()
				.orElse(new Configuration(0L, SPLATOON3_DEFAULT_USER_AGENT_CONFIG_NAME, null));

			if (!loadedUserAgent.equals(defaultUserAgentConfigs.getConfigValue())) {
				defaultUserAgentConfigs.setConfigValue(loadedUserAgent);

				configurationRepository.save(defaultUserAgentConfigs);
				logSender.sendLogs(logger, String.format("Saved newest DefaultUserAgent: `%s`", loadedUserAgent));
			}
		}
	}

	public static String getDefaultUserAgent() {
		return DEFAULT_USER_AGENT;
	}

	private AuthenticatedHttpClientCreator clientCreator;

	@Autowired
	public void setClientCreator(AuthenticatedHttpClientCreator clientCreator) {
		this.clientCreator = clientCreator;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	private final ObjectMapper mapper = new ObjectMapper();

	public <T> T querySplatoonApiForAccount(Account account, String path, Class<T> valueType) {
		TimeZone tz = TimeZone.getDefault();
		int offset = tz.getOffset(new Date().getTime()) / 1000 / 60;

		String host = "https://app.splatoon2.nintendo.net";
		String address = host + path;

		URI uri = URI.create(address);

		String appUniqueId = "32449507786579989235";
		HttpRequest request = HttpRequest.newBuilder()
			.GET()
			.uri(uri)
			.setHeader("x-unique-id", appUniqueId)
			.setHeader("x-requested-with", "XMLHttpRequest")
			.setHeader("x-timezone-offset", String.format("%d", offset))
//			.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
			.setHeader("User-Agent", getDefaultUserAgent())
			.setHeader("Accept", "*/*")
			.setHeader("Referer", "https://app.splatoon2.nintendo.net/home")
			.setHeader("Accept-Encoding", "gzip, deflate")
			.setHeader("Accept-Language", "en-US")
			.build();

		return sendRequestAndParseGzippedJson(account, request, valueType);
	}

	private <T> T sendRequestAndParseGzippedJson(Account account, HttpRequest request, Class<T> valueType) {
		String body = "";
		int retryCount = 0;

		while (retryCount < 5) {
			try {
				logger.debug("RequestSender sending new request to '{}'", request.uri().toString());
				HttpResponse<byte[]> response = clientCreator.createFor(account).send(request, HttpResponse.BodyHandlers.ofByteArray());

				logger.debug("got response with status code {}:", response.statusCode());

				if (response.statusCode() < 300) {
					body = new String(response.body());

					if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
						body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
					}

					return mapper.readValue(body, valueType);
				} else {
					logger.info("request:");
					logger.info(request);
					logger.info("response:");
					logger.info(response);

					return null;
				}
			} catch (IOException | InterruptedException e) {
				if (e instanceof IOException && e.getCause() != null && e.getCause() instanceof CookieRefreshException) {
					discordBot.sendPrivateMessage(account.getDiscordId(), "**ERROR** your cookie to access to splatnet became outdated, I cannot access splatnet anymore.\nPlease provide new login credentials by using the **!splatoon2 register** command.");
					logger.error("The cookie for account with id {} wasn't valid anymore and no session token has been set!", account.getId());

					return null;
				} else {
					// log and retry in a second

					logger.error("exception while sending request, retrying...");
					logger.error("response body: '{}'", body);

					logger.error(e);

					retryCount++;
					logger.error("retry count: {} of 4", retryCount);

					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}

		return null;
	}
}
