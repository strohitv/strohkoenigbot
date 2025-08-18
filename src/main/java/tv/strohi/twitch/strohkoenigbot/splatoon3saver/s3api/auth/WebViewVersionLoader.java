package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.S3CookieHandler;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.S3RequestSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth.S3Authenticator.SPLATOON3_WEBVIEWVERSION_CONFIG_NAME;

@Component
@RequiredArgsConstructor
public class WebViewVersionLoader implements ScheduledService {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final S3RequestSender s3RequestSender;
	private final LogSender logSender;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;

	public String refreshWebViewVersion(String gToken) {
		var client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(120))
			.version(HttpClient.Version.HTTP_2)
			.cookieHandler(new S3CookieHandler(gToken, true))
			.build();

		String homepageAddress = "https://api.lp1.av5ja.srv.nintendo.net";

		URI homePageUri = URI.create(homepageAddress);

		HttpRequest homePageRequest = HttpRequest.newBuilder()
			.GET()
			.uri(homePageUri)
			.setHeader("Accept", "*/*")
			.setHeader("DNT", "1")
			.setHeader("X-AppColorScheme", "DARK")
			.setHeader("X-Requested-With", "com.nintendo.znca")
			.setHeader("Sec-Fetch-Site", "none")
			.setHeader("Sec-Fetch-Mode", "navigate")
			.setHeader("Sec-Fetch-User", "?1")
			.setHeader("Sec-Fetch-Dest", "document")
			.setHeader("Accept-Language", "en-US")
			.setHeader("Accept-Encoding", "gzip,deflate,br")
//			.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
			.setHeader("User-Agent", RequestSender.getDefaultUserAgent())
			.build();

		String webViewVersion = null;

		try {
			String homepageHtml = s3RequestSender.sendRequestAndParseGzippedJson(client, homePageRequest);
			logger.debug(homepageHtml);

			if (homepageHtml != null) {
				Document doc = Jsoup.parse(homepageHtml);
				String mainJsPath = doc.select("script").stream()
					.filter(script -> script.attributes().asList().stream().anyMatch(a ->
						"src".equals(a.getKey())
							&& a.getValue() != null
							&& a.getValue().contains("main.") && a.getValue().contains(".js")))
					.map(script -> script.attributes().get("src"))
					.findFirst()
					.orElse(null);

				if (mainJsPath != null) {
					String mainJsAddress = homepageAddress + mainJsPath;

					URI mainJsUri = URI.create(mainJsAddress);

					HttpRequest mainJsRequest = HttpRequest.newBuilder()
						.GET()
						.uri(mainJsUri)
						.setHeader("Accept", "*/*")
						.setHeader("Referer", homepageAddress)
						.setHeader("Sec-Fetch-Site", "same-origin")
						.setHeader("Sec-Fetch-Mode", "no-cors")
						.setHeader("Sec-Fetch-Dest", "script")
						.setHeader("Accept-Language", "en-US")
						.setHeader("Accept-Encoding", "gzip,deflate,br")
//			.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
						.setHeader("User-Agent", RequestSender.getDefaultUserAgent())
						.build();

					String mainJsContent = s3RequestSender.sendRequestAndParseGzippedJson(client, mainJsRequest);
					logger.debug(mainJsContent);

					if (mainJsContent != null) {
						Matcher mainJsMatcher = Pattern
							.compile("\\b(?<revision>[\\da-f]{40})\\b\\S*?void 0\\S*?\"revision_info_not_set\"}`,.*?=`(?<version>\\d+\\.\\d+\\.\\d+)-")
							.matcher(mainJsContent);

						if (mainJsMatcher.find()) {
							String revision = mainJsMatcher.group("revision");
							String version = mainJsMatcher.group("version");

							webViewVersion = String.format("%s-%s", version, revision.substring(0, 8));
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("couldn't receive web view version");
			logger.error(e);
		}

		return webViewVersion;
	}

	private void refreshWebViewVersion() {
		accountRepository.findByEnableSplatoon3(true)
			.forEach(account -> {
				String webViewVersion = refreshWebViewVersion(account.getGTokenSplatoon3());
				logger.debug("webViewVersion");
				logger.debug(webViewVersion);

				if (webViewVersion != null) {
					Configuration webViewConfigs = configurationRepository.findAllByConfigName(SPLATOON3_WEBVIEWVERSION_CONFIG_NAME).stream()
						.findFirst()
						.orElse(new Configuration(0L, SPLATOON3_WEBVIEWVERSION_CONFIG_NAME, null));

					if (!webViewVersion.equals(webViewConfigs.getConfigValue())) {
						webViewConfigs.setConfigValue(webViewVersion);

						configurationRepository.save(webViewConfigs);
						logSender.sendLogs(logger, String.format("Saved newest WebViewVersion: **%s**", webViewVersion));
					}
				}
			});
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of();
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of(ScheduleRequest.builder()
			.name("WebViewVersionLoader_schedule")
			.schedule(TickSchedule.getScheduleString(TickSchedule.ofMinutes(5)))
			.runnable(this::refreshWebViewVersion)
			.build());
	}
}
