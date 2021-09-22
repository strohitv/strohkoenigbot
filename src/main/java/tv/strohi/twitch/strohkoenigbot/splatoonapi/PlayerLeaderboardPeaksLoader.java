package tv.strohi.twitch.strohkoenigbot.splatoonapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonPlayerPeaks;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatoonXRankLeaderBoard;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

@Component
public class PlayerLeaderboardPeaksLoader {
	private final String appUniqueId = "15228359194300492953";
	private final DateTimeFormatter FMT = new DateTimeFormatterBuilder()
			.appendPattern("yyyy-MM-dd")
			.parseDefaulting(ChronoField.NANO_OF_DAY, 0)
			.toFormatter()
			.withZone(ZoneId.of("Europe/Berlin"));

	private HttpClient client;

	@Autowired
	public void setClient(HttpClient client) {
		this.client = client;
	}

	private final ObjectMapper mapper = new ObjectMapper();

	public SplatoonPlayerPeaks getPlayerPeaks() {
		SplatoonPlayerPeaks peaks = new SplatoonPlayerPeaks();

		int year = 2018;
		int month = 5;

		while (Instant.now().isAfter(Instant.parse(String.format("%04d-%02d-01T01:00:00.00Z", year, month)))) {
			SplatoonXRankLeaderBoard leaderBoard = getLeaderBoard(year, month);

			if (leaderBoard.getRainmaker().getMy_ranking() != null && leaderBoard.getRainmaker().getMy_ranking().getX_power() > peaks.getRainmakerPeak()) {
				peaks.setRainmakerPeak(leaderBoard.getRainmaker().getMy_ranking().getX_power());
				peaks.setRainmakerMonth(Instant.ofEpochSecond(leaderBoard.getRainmaker().getStart_time()));
				peaks.setRainmakerPeakRank(leaderBoard.getRainmaker().getMy_ranking().getRank());
				peaks.setRainmakerPeakWeaponImage(String.format("https://app.splatoon2.nintendo.net%s", leaderBoard.getRainmaker().getMy_ranking().getWeapon().getImage()));
			}

			if (leaderBoard.getSplat_zones().getMy_ranking() != null && leaderBoard.getSplat_zones().getMy_ranking().getX_power() > peaks.getZonesPeak()) {
				peaks.setZonesPeak(leaderBoard.getSplat_zones().getMy_ranking().getX_power());
				peaks.setZonesMonth(Instant.ofEpochSecond(leaderBoard.getSplat_zones().getStart_time()));
				peaks.setZonesPeakRank(leaderBoard.getSplat_zones().getMy_ranking().getRank());
				peaks.setZonesPeakWeaponImage(String.format("https://app.splatoon2.nintendo.net%s", leaderBoard.getSplat_zones().getMy_ranking().getWeapon().getImage()));
			}

			if (leaderBoard.getTower_control().getMy_ranking() != null && leaderBoard.getTower_control().getMy_ranking().getX_power() > peaks.getTowerPeak()) {
				peaks.setTowerPeak(leaderBoard.getTower_control().getMy_ranking().getX_power());
				peaks.setTowerMonth(Instant.ofEpochSecond(leaderBoard.getTower_control().getStart_time()));
				peaks.setTowerPeakRank(leaderBoard.getTower_control().getMy_ranking().getRank());
				peaks.setTowerPeakWeaponImage(String.format("https://app.splatoon2.nintendo.net%s", leaderBoard.getTower_control().getMy_ranking().getWeapon().getImage()));
			}

			if (leaderBoard.getClam_blitz().getMy_ranking() != null && leaderBoard.getClam_blitz().getMy_ranking().getX_power() > peaks.getClamsPeak()) {
				peaks.setClamsPeak(leaderBoard.getClam_blitz().getMy_ranking().getX_power());
				peaks.setClamsMonth(Instant.ofEpochSecond(leaderBoard.getClam_blitz().getStart_time()));
				peaks.setClamsPeakRank(leaderBoard.getClam_blitz().getMy_ranking().getRank());
				peaks.setClamsPeakWeaponImage(String.format("https://app.splatoon2.nintendo.net%s", leaderBoard.getClam_blitz().getMy_ranking().getWeapon().getImage()));
			}

			year = month < 12 ? year : year + 1;
			month = (month % 12) + 1;
		}

		return peaks;
	}

	private SplatoonXRankLeaderBoard getLeaderBoard(int year, int month) {
		int endYear = month < 12 ? year : year + 1;
		int endMonth = (month % 12) + 1;

		if (year == 2018 && month == 5) {
			month = 4;
		}

		String address = String.format("https://app.splatoon2.nintendo.net/api/x_power_ranking/%02d%02d01T00_%02d%02d01T00/summary", year - 2000, month, endYear - 2000, endMonth);

		TimeZone tz = TimeZone.getDefault();
		int offset = tz.getOffset(new Date().getTime()) / 1000 / 60;

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(uri)
				.setHeader("x-unique-id", appUniqueId)
				.setHeader("x-requested-with", "XMLHttpRequest")
				.setHeader("x-timezone-offset", String.format("%d", offset))
				.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 7.1.2; Pixel Build/NJH47D; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/59.0.3071.125 Mobile Safari/537.36")
				.setHeader("Accept", "*/*")
				.setHeader("Referer", "https://app.splatoon2.nintendo.net/home")
				.setHeader("Accept-Encoding", "gzip, deflate")
				.setHeader("Accept-Language", "en-US")
				.build();

		return sendRequestAndParseGzippedJson(request, SplatoonXRankLeaderBoard.class);
	}

	private <T> T sendRequestAndParseGzippedJson(HttpRequest request, Class<T> valueType) {
		try {
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() < 300) {
				String body = new String(response.body());

				if (response.headers().map().containsKey("Content-Encoding") && !response.headers().map().get("Content-Encoding").isEmpty() && "gzip".equals(response.headers().map().get("Content-Encoding").get(0))) {
					body = new String(new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes());
				}

				return mapper.readValue(body, valueType);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		return null;
	}
}
