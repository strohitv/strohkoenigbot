package tv.strohi.twitch.strohkoenigbot.chatbot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class TwitchBotClientTest {
	@BeforeEach
	void setUp() {
	}

	@AfterEach
	void tearDown() {
	}

	@Test
	void getZoneId() {
		Set<String> zids = ZoneId.getAvailableZoneIds();

		for (int i = 0; i < 24; i++) {
			int currentHour = i;

			List<String> possibleZids = zids.stream().filter(z -> Instant.now().atZone(ZoneId.of(z)).getHour() == currentHour).sorted().collect(Collectors.toList());

			StringBuilder builder = new StringBuilder();
			AtomicInteger test = new AtomicInteger(1);
			possibleZids.forEach(pz -> builder.append(test.getAndIncrement()).append(": ")
					.append(pz)
//					.append(" (")
//					.append(String.format("%02d", Instant.now().atZone(ZoneId.of(pz)).getHour()))
//					.append(":")
//					.append(Instant.now().atZone(ZoneId.of(pz)).getMinute())
//					.append(")")
					.append("\n"));

			String timezoneString = builder.toString();
			System.out.printf("%d: %s%n", currentHour, timezoneString.length());

			assert timezoneString.length() < 2000;
		}
	}
}
