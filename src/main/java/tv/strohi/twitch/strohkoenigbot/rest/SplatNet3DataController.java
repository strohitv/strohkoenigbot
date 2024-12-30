package tv.strohi.twitch.strohkoenigbot.rest;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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

	private final Map<String, Object> data = new HashMap<>();

	public SplatNet3DataController() {
		var now = Instant.now().toEpochMilli();

		data.put(ROTATIONS_KEY, Map.of());
		data.put(String.format("%s%s", ROTATIONS_KEY, REFRESH_KEY), now);

		data.put(STORE_KEY, Map.of());
		data.put(String.format("%s%s", STORE_KEY, REFRESH_KEY), now);
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
	public @ResponseBody Map<String, Object> getS3Overlay() {
		return data;
	}
}
