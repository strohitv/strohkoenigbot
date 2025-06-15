package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3RequestKey;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3RequestKey;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.Splatoon3RequestKeyRepository;
import tv.strohi.twitch.strohkoenigbot.utils.ExceptionSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Log4j2
@Service
@RequiredArgsConstructor
public class S3RequestKeyLoader implements ScheduledService {
	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of();
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3RequestKeyLoader_refreshRequestKeys")
			.schedule(TickSchedule.getScheduleString(TickSchedule.everyMinutes(1)))
			.runnable(this::refreshRequestKeys)
			.build());
	}

	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;
	private final ExceptionSender exceptionSender;

	private final AccountRepository accountRepository;
	private final ConfigurationRepository configurationRepository;
	private final Splatoon3RequestKeyRepository requestKeyRepository;
	private final RestTemplate restTemplate;

	private void refreshRequestKeys() {
		try {
			var account = accountRepository.findByIsMainAccount(true).stream().findFirst();
			if (account.isPresent()) {
				var requestHeaders = new HttpHeaders();
				requestHeaders.add("Cookie", "_gtoken=" + account.get().getGTokenSplatoon3());
				var requestEntity = new HttpEntity(null, requestHeaders);

				var splatNetHomepageResponse = restTemplate.exchange("https://api.lp1.av5ja.srv.nintendo.net/", HttpMethod.GET, requestEntity, String.class);
				if (!splatNetHomepageResponse.hasBody()) {
					logSender.sendLogs(log, "## Error\nCould not load SplatNet3 main page, splatNetHomepageResponse has no body!");
					return;
				}

				var htmlBody = Optional.ofNullable(splatNetHomepageResponse.getBody()).orElse("");
				var htmlPattern = Pattern.compile("<script.* src=\"(?<url>/static/js/main\\..+\\.js)\"></script>");
				var htmlMatcher = htmlPattern.matcher(htmlBody);
				if (htmlMatcher.find()) {
					var scriptAddress = htmlMatcher.group("url");

					if (scriptAddress == null || scriptAddress.isEmpty()) {
						logSender.sendLogs(log, "## Error\nCould not load SplatNet3 main js file, scriptAddress is empty!");
						return;
					}

					var mainJsPathConfig = configurationRepository.findByConfigName("Splatoon3_MainJsPath")
						.orElseGet(() ->
							configurationRepository.save(Configuration.builder()
								.configName("Splatoon3_MainJsPath")
								.configValue("")
								.build()));

					if (Objects.equals(mainJsPathConfig.getConfigValue(), scriptAddress)) {
						return;
					}

					logSender.sendLogs(log, "## New main.js path found\nFound new main.js path: `%s`", scriptAddress);
					configurationRepository.save(mainJsPathConfig.toBuilder().configValue(scriptAddress).build());

					var scriptUrl = new URL("https://api.lp1.av5ja.srv.nintendo.net/").toURI().resolve(scriptAddress).toString();

					var splatNetJsContent = restTemplate.getForObject(scriptUrl, String.class);
					if (splatNetJsContent == null || splatNetJsContent.isEmpty()) {
						logSender.sendLogs(log, "## Error\nCould not load SplatNet3 main page, splatNetJsContent is null or empty!");
						return;
					}

					var jsPattern = Pattern.compile("\\{.*(\\s|,)?id:\\s*\"(?<id>[a-zA-Z0-9]*)\".*(\\s|,)name:\\s*\"(?<name>[a-zA-Z0-9]*)\".*}");

					var startIndex = -1;
					while ((startIndex = splatNetJsContent.indexOf("operationKind:\"query\"", startIndex + 1)) > -1) {
						var chunkStartIndex = startIndex;
						var chunk = "";

						while(!(chunk = splatNetJsContent.substring(chunkStartIndex, splatNetJsContent.indexOf('}', startIndex) + 1)).contains("id")) {
							chunkStartIndex = splatNetJsContent.lastIndexOf('{', chunkStartIndex - 1);
						}

						var jsMatcher = jsPattern.matcher(chunk);

						while (jsMatcher.find()) {
							var operationName = jsMatcher.group("name");
							var operationId = jsMatcher.group("id");

							var requestKey = requestKeyRepository.findByQueryName(operationName)
								.orElse(Splatoon3RequestKey.builder()
									.queryName(operationName)
									.build());

							if (Objects.equals(requestKey.getQueryHash(), operationId)) {
								continue;
							}

							requestKey.setQueryHash(operationId);
							logSender.sendLogs(log, String.format("Found new query hash for `%s` via SplatNet3: `%s`", requestKey.getQueryName(), operationId));

							Arrays.stream(S3RequestKey.values()).filter(rk -> rk.getKey().equals(operationId))
								.findFirst()
								.ifPresent(rk -> requestKey.setQueryPath(rk.getPath()));

							requestKeyRepository.save(requestKey);
						}
					}
				}
			} else {
				logSender.sendLogs(log, "## Error\nCould not find main account in S3RequestKeyLoader!");
			}
		} catch (Exception ex) {
			exceptionLogger.logException(log, ex);
			exceptionSender.send(ex);
		}
	}
}
