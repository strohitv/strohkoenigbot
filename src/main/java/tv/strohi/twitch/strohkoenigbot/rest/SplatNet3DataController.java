package tv.strohi.twitch.strohkoenigbot.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.rest.model.S3Tokens;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3ReplayCodeLoader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3TokenRefresher;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequestMapping("v1")
@Controller
@Log4j2
public class SplatNet3DataController {
	public static final String ROTATIONS_KEY = "rotations";
	public static final String STORE_KEY = "store";

	private final String REFRESH_KEY = "_last_refresh";

	private final S3TokenRefresher s3TokenRefresher;
	private final ConfigurationRepository configurationRepository;
	private final AccountRepository accountRepository;

	private final LogSender logSender;

	private final S3ReplayCodeLoader replayCodeLoader;

	private final Map<String, Bucket> buckets;

	private final Map<String, Object> data = new HashMap<>();

	public SplatNet3DataController(S3TokenRefresher s3TokenRefresher, ConfigurationRepository configurationRepository, LogSender logSender, AccountRepository accountRepository, S3ReplayCodeLoader replayCodeLoader) {
		this.s3TokenRefresher = s3TokenRefresher;
		this.configurationRepository = configurationRepository;
		this.accountRepository = accountRepository;
		this.logSender = logSender;
		this.replayCodeLoader = replayCodeLoader;

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
				.build(),
			"get-tokens", Bucket.builder()
				.addLimit(Bandwidth.builder()
					.capacity(5)
					.refillGreedy(5, Duration.ofMinutes(1))
					.build())
				.build(),
			"gtoken", Bucket.builder()
				.addLimit(Bandwidth.builder()
					.capacity(5)
					.refillGreedy(5, Duration.ofMinutes(1))
					.build())
				.build(),
			"get-replay-queue", Bucket.builder()
				.addLimit(Bandwidth.builder()
					.capacity(5)
					.refillGreedy(5, Duration.ofMinutes(1))
					.build())
				.build(),
			"upload-replay-json", Bucket.builder()
				.addLimit(Bandwidth.builder()
					.capacity(5)
					.refillGreedy(5, Duration.ofMinutes(1))
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
			var authCheckResult = doAuthCheck(auth, "s3s config file upload", Void.class);

			if (authCheckResult.isPresent()) {
				return authCheckResult.get();
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

	@GetMapping(value = "gtoken")
	@CrossOrigin(
		origins = {"https://api.lp1.av5ja.srv.nintendo.net"},
		allowCredentials = "true")
	public ResponseEntity<String> getGToken(@RequestHeader("Authorization") String auth) {
		if (buckets.get("gtoken").tryConsume(1)) {
			var authCheckResult = doAuthCheck(auth, "gToken retrieval", String.class);

			if (authCheckResult.isPresent()) {
				return authCheckResult.get();
			}

			var account = accountRepository.findByIsMainAccount(true).stream()
				.findFirst()
				.orElse(null);

			if (account == null) {
				logSender.queueLogs(log, "### ERROR during gToken retrieval!\nNo main account found!");
				return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
			}

			logSender.queueLogs(log, "Someone successfully loaded gToken!");
			return ResponseEntity.ok(account.getGTokenSplatoon3());
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}

	@GetMapping(value = "get-tokens")
	public ResponseEntity<S3Tokens> getTokens(@RequestHeader("Authorization") String auth) {
		if (buckets.get("get-tokens").tryConsume(1)) {
			var authCheckResult = doAuthCheck(auth, "token loading", S3Tokens.class);

			if (authCheckResult.isPresent()) {
				return authCheckResult.get();
			}

			var account = accountRepository.findByIsMainAccount(true).stream()
				.findFirst()
				.orElse(null);

			if (account == null) {
				logSender.queueLogs(log, "### ERROR during token loading!\nNo main account found!");
				return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
			}

			logSender.queueLogs(log, "Someone successfully loaded gToken and bulletToken!");
			return ResponseEntity.ok(new S3Tokens(account.getGTokenSplatoon3(), account.getBulletTokenSplatoon3()));
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}

	@GetMapping(value = "get-replay-queue")
	public ResponseEntity<List<String>> getReplayQueue(@RequestHeader("Authorization") String auth) {
		if (buckets.get("get-replay-queue").tryConsume(1)) {
			var authCheckResult = doAuthCheck(auth, "replay code list download", new TypeReference<List<String>>() {
			});

			return authCheckResult.orElseGet(() -> ResponseEntity.ok().body(replayCodeLoader.getReplayQueue()));

		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}

	@PostMapping(value = "/replay/{replayCode}")
	public ResponseEntity<Void> uploadReplayJson(@RequestHeader("Authorization") String auth, @PathVariable String replayCode, @RequestBody String replayResult) {
		if (buckets.get("upload-replay-json").tryConsume(1)) {
			var authCheckResult = doAuthCheck(auth, "replay json upload", Void.class);

			if (authCheckResult.isPresent()) {
				return authCheckResult.get();
			}

			if (replayCodeLoader.addReplayData(replayCode, replayResult)) {
				return ResponseEntity.ok().build();
			}

			return ResponseEntity.badRequest().build();
		}

		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
	}

	private <T> Optional<ResponseEntity<T>> doAuthCheck(String auth, String caller, Class<T> clazz) {
		var typeRef = new TypeReference<T>() {
			@Override
			public Type getType() {
				return clazz;
			}
		};
		return doAuthCheck(auth, caller, typeRef);
	}

	private <T> Optional<ResponseEntity<T>> doAuthCheck(String auth, String caller, TypeReference<T> typeRef) {
		var user = configurationRepository.findAllByConfigName("uploadS3sConfigUser").stream().findFirst();
		var pass = configurationRepository.findAllByConfigName("uploadS3sConfigPassword").stream().findFirst();

		if (user.isEmpty() || pass.isEmpty()) {
			logSender.queueLogs(log, "### ERROR during %s!\nAuth credentials could not be found!", caller);
			return Optional.of(ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build());
		}

		var comparisonString = String.format("Basic %s", Base64.encodeBase64String(String.format("%s:%s", user.get().getConfigValue(), pass.get().getConfigValue()).getBytes(StandardCharsets.UTF_8)));
		if (!comparisonString.equals(auth)) {
			logSender.queueLogs(log, "### ERROR during %s!\nUser and/or password were not correct!", caller);
			return Optional.of(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
		}

		return Optional.empty();
	}
}
