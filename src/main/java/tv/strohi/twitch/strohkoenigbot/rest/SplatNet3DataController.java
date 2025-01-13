package tv.strohi.twitch.strohkoenigbot.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3TokenRefresher;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.ConfigFile;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
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
	private final ObjectMapper mapper;

	private final Map<String, Bucket> buckets;

	private final Map<String, Object> data = new HashMap<>();

	@Setter
	private static long nextTimeTokenExpires = Instant.now().getEpochSecond();

	public SplatNet3DataController(S3TokenRefresher s3TokenRefresher, ConfigurationRepository configurationRepository, LogSender logSender, ObjectMapper mapper) {
		this.s3TokenRefresher = s3TokenRefresher;
		this.configurationRepository = configurationRepository;
		this.logSender = logSender;
		this.mapper = mapper;

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
			"upload-s3s-config", Bucket.builder()
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
			return ResponseEntity.ok(String.format("%d", (nextTimeTokenExpires - Instant.now().getEpochSecond()) / 60));
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}

	@PostMapping(value = "upload-s3s-config", consumes = "application/json")
	public ResponseEntity<Void> uploadConfig(@RequestHeader("Authorization") String auth, @RequestBody ConfigFile configFile) {
		if (buckets.get("upload-s3s-config").tryConsume(1)) {
			var user = configurationRepository.findAllByConfigName("uploadS3sConfigUser").stream().findFirst();
			var pass = configurationRepository.findAllByConfigName("uploadS3sConfigPassword").stream().findFirst();

			if (user.isEmpty() || pass.isEmpty()) {
				logSender.sendLogs(log, "### ERROR during s3s config file upload!\nAuth credentials could not be found!");
				return ResponseEntity.internalServerError().build();
			}

			var comparisonString = String.format("Basic %s", Base64.encodeBase64String(String.format("%s:%s", user, pass).getBytes(StandardCharsets.UTF_8)));
			if (!comparisonString.equals(auth)) {
				logSender.sendLogs(log, "### ERROR during s3s config file upload!\nUser and/or password were not correct!");
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			}

			var oldNextTimeTokenExpires = nextTimeTokenExpires;

			var s3sLocation = configurationRepository.findAllByConfigName("s3sLocation").stream().findFirst();
			if (s3sLocation.isEmpty() || !new File(s3sLocation.get().getConfigValue()).exists()) {
				logSender.sendLogs(log, "### ERROR during s3s config file upload!\nConfig file directory could not be found!");
				return ResponseEntity.internalServerError().build();
			}

			var s3sConfigFileLocation = new File(Paths.get(s3sLocation.get().getConfigValue(), "config.txt").toString());

			try {
				mapper.writeValue(s3sConfigFileLocation, configFile);
			} catch (IOException e) {
				logSender.sendLogs(log, "### ERROR during s3s config file upload!\nCould not write config file to disk!");
				return ResponseEntity.badRequest().build();
			}

			s3TokenRefresher.refreshToken();

			if (nextTimeTokenExpires == oldNextTimeTokenExpires) {
				// refresh could not get newer tokens
				return ResponseEntity.badRequest().build();
			}

			return ResponseEntity.ok().build();
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}
}
