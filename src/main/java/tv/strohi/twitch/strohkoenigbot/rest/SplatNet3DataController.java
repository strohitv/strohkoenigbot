package tv.strohi.twitch.strohkoenigbot.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3TokenRefresher;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RequestMapping("v1")
@Controller
@Log4j2
public class SplatNet3DataController {
	public static final String ROTATIONS_KEY = "rotations";
	public static final String STORE_KEY = "store";

	private final String REFRESH_KEY = "_last_refresh";

	private final S3TokenRefresher s3TokenRefresher;
	private final ConfigurationRepository configurationRepository;
	private final LogSender logSender;

	private final Map<String, Bucket> buckets;

	private final Map<String, Object> data = new HashMap<>();

	public SplatNet3DataController(S3TokenRefresher s3TokenRefresher, ConfigurationRepository configurationRepository, LogSender logSender, ObjectMapper mapper) {
		this.s3TokenRefresher = s3TokenRefresher;
		this.configurationRepository = configurationRepository;
		this.logSender = logSender;

		var now = Instant.now().toEpochMilli();

		data.put(ROTATIONS_KEY, Map.of());
		data.put(String.format("%s%s", ROTATIONS_KEY, REFRESH_KEY), now);

		data.put(STORE_KEY, Map.of());
		data.put(String.format("%s%s", STORE_KEY, REFRESH_KEY), now);

		this.buckets = Map.of(
			"splatnet3-data", Bucket.builder()
				.addLimit(Bandwidth.builder()
					.capacity(10)
					.refillGreedy(10, Duration.ofMinutes(1))
					.build())
				.build(),
			"remaining-token-duration-minutes", Bucket.builder()
				.addLimit(Bandwidth.builder()
					.capacity(10)
					.refillGreedy(10, Duration.ofMinutes(1))
					.build())
				.build(),
			"refresh-tokens", Bucket.builder()
				.addLimit(Bandwidth.builder()
					.capacity(10)
					.refillGreedy(10, Duration.ofMinutes(1))
					.build())
				.build());
	}

	public void refresh(String key, Object newValue) {
		if (data.containsKey(key) && !key.toLowerCase().contains(REFRESH_KEY)) {
			data.put(key, newValue);
			data.put(String.format("%s%s", key, REFRESH_KEY), Instant.now().toEpochMilli());
		} else {
			log.error("Blocked attempt to insert new key '{}' into SplatNet data map.", key);
		}
	}

	@GetMapping(value = "splatnet3-data", produces = "application/json")
	public ResponseEntity<Map<String, Object>> getS3Overlay() {
		if (buckets.get("splatnet3-data").tryConsume(1)) {
			return ResponseEntity.ok(data);
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}

	@GetMapping(value = "remaining-token-duration-minutes", produces = "text/plain")
	public ResponseEntity<String> getRemaining() {
		if (buckets.get("remaining-token-duration-minutes").tryConsume(1)) {
			var config = configurationRepository.findByConfigName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME)
				.orElse(Configuration.builder().configName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME).configValue(String.format("%d", Instant.now().getEpochSecond())).build());

			var nextTimeTokenExpires = Long.parseLong(config.getConfigValue());

			return ResponseEntity.ok(String.format("%d", (nextTimeTokenExpires - Instant.now().getEpochSecond()) / 60));
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}

	@PostMapping(value = "refresh-tokens")
	public ResponseEntity<Void> uploadConfig(@RequestHeader("Authorization") String auth) {
		if (buckets.get("refresh-tokens").tryConsume(1)) {
			var user = configurationRepository.findAllByConfigName("uploadS3sConfigUser").stream().findFirst();
			var pass = configurationRepository.findAllByConfigName("uploadS3sConfigPassword").stream().findFirst();

			if (user.isEmpty() || pass.isEmpty()) {
				logSender.sendLogs(log, "### ERROR during s3s config file upload!\nAuth credentials could not be found!");
				return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
			}

			var comparisonString = String.format("Basic %s", Base64.encodeBase64String(String.format("%s:%s", user.get().getConfigValue(), pass.get().getConfigValue()).getBytes(StandardCharsets.UTF_8)));
			if (!comparisonString.equals(auth)) {
				logSender.sendLogs(log, "### ERROR during s3s config file upload!\nUser and/or password were not correct!");
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			}

			var config = configurationRepository.findByConfigName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME)
				.orElse(Configuration.builder().configName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME).configValue(String.format("%d", Instant.now().getEpochSecond())).build());

			var oldNextTimeTokenExpires = Long.parseLong(config.getConfigValue());

			s3TokenRefresher.refreshToken();

			var newConfig = configurationRepository.findByConfigName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME)
				.orElse(Configuration.builder().configName(S3TokenRefresher.SPLATNET_3_TOKEN_EXPIRATION_CONFIG_NAME).configValue(String.format("%d", Instant.now().getEpochSecond())).build());

			var nextTimeTokenExpires = Long.parseLong(newConfig.getConfigValue());

			if (nextTimeTokenExpires == oldNextTimeTokenExpires) {
				// refresh could not find newer tokens
				return ResponseEntity.notFound().build();
			}

			return ResponseEntity.ok().build();
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}
}
