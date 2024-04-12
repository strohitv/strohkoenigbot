package tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3RequestKey;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3RequestKey;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.Splatoon3RequestKeyRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.NsoQueryKeyData;
import tv.strohi.twitch.strohkoenigbot.utils.ExceptionSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class S3RequestKeyLoader {
	private final SchedulingService schedulingService;

	@PostConstruct
	public void registerSchedule() {
		schedulingService.registerOnce("S3RequestKeyLoader_refreshRequestKeys", TickSchedule.everyMinutes(1), this::refreshRequestKeys);
	}

	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;
	private final ExceptionSender exceptionSender;

	private final ConfigurationRepository configurationRepository;
	private final Splatoon3RequestKeyRepository requestKeyRepository;
	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	private void refreshRequestKeys() {
		try {
			var queryKeysResponse = restTemplate.getForObject("https://raw.githubusercontent.com/imink-app/SplatNet3/master/Data/splatnet3_webview_data.json", String.class);

			var queryKeys = objectMapper.readValue(queryKeysResponse, NsoQueryKeyData.class);

			if (queryKeys == null) {
				return;
			}

			if (queryKeys.getVersion() != null) {
				var webViewVersionConfig = configurationRepository.findByConfigName("Splatoon3_WebViewVersion");
				var shouldRefreshVersion = false;
				if (webViewVersionConfig.isPresent()) {
					var dbVersion = Arrays.stream(webViewVersionConfig.get().getConfigValue().split("-")[0].split("\\."))
						.map(Integer::parseInt)
						.collect(Collectors.toList());

					var jsonVersion = Arrays.stream(queryKeys.getVersion().split("-")[0].split("\\."))
						.map(Integer::parseInt)
						.collect(Collectors.toList());

					if (jsonVersion.get(0) * 1000000 + jsonVersion.get(1) * 1000 + jsonVersion.get(2) > dbVersion.get(0) * 1000000 + dbVersion.get(1) * 1000 + dbVersion.get(2)) {
						shouldRefreshVersion = true;
					}
				} else {
					shouldRefreshVersion = true;
				}

				if (shouldRefreshVersion) {
					configurationRepository.save(webViewVersionConfig.map(wvvc -> {
						wvvc.setConfigValue(queryKeys.getVersion());
						return wvvc;
					}).orElse(Configuration.builder()
						.configName("Splatoon3_WebViewVersion")
						.configValue(queryKeys.getVersion())
						.build()));

					logSender.sendLogs(log, String.format("Found a newer version via github request keys: `%s`", queryKeys.getVersion()));
				}
			}

			if (queryKeys.getGraphql() != null && queryKeys.getGraphql().getHash_map() != null) {
				queryKeys.getGraphql().getHash_map().forEach((name, hash) -> {
					var requestKey = requestKeyRepository.findByQueryName(name)
						.orElse(Splatoon3RequestKey.builder()
							.queryName(name)
							.build());

					if (!Objects.equals(requestKey.getQueryHash(), hash)) {
						requestKey.setQueryHash(hash);
						logSender.sendLogs(log, String.format("Found a new query hash for request key `%s` via github: `%s`", requestKey.getQueryName(), hash));
					}

					Arrays.stream(S3RequestKey.values()).filter(rk -> rk.getKey().equals(hash))
						.findFirst()
						.ifPresent(rk -> requestKey.setQueryPath(rk.getPath()));

					requestKeyRepository.save(requestKey);
				});
			}
		} catch (Exception ex) {
			exceptionLogger.logException(log, ex);
			exceptionSender.send(ex);
		}
	}
}
