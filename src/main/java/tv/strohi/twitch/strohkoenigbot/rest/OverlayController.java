package tv.strohi.twitch.strohkoenigbot.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.sendou.SendouService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3BadgeSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3Downloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3EmoteSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3StreamStatistics;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ExtendedStatisticsExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.Statistics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

@RequestMapping("overlay")
@Controller
@RequiredArgsConstructor
@Log4j2
public class OverlayController {
	private static final Pattern NUMBER_PATTERN = Pattern.compile("^[0-9]+$");
	private static final Pattern BOOLEAN_NUMBER_PATTERN = Pattern.compile("^[01]$");

	private final Statistics statistics;
	private final S3StreamStatistics s3StreamStatistics;
	private final ExtendedStatisticsExporter fullscreenExporter;
	private final S3BadgeSender badgeSender;
	private final S3EmoteSender emoteSender;
	private final TwitchBotClient twitchBotClient;
	private final S3Downloader s3Downloader;
	private final SendouService sendouService;
	private final LogSender logSender;
	private final AccountRepository accountRepository;

	@GetMapping("s3")
	public @ResponseBody String getS3Overlay(
		@RequestParam(name = "sendou_id", required = false, defaultValue = "6238") String sendouUserQueryString,
		@RequestParam(name = "tournament_id", required = false, defaultValue = "") String tournamentString,
		@RequestParam(name = "search_sendouq", required = false, defaultValue = "1") String searchSendouQString) {
		if (twitchBotClient.getWentLiveTime() != null) {
			var lastUpdateTime = s3StreamStatistics.getLastUpdate();
			if (lastUpdateTime == null || Instant.now().minus(10, ChronoUnit.MINUTES).isAfter(lastUpdateTime)) {
//				logSender.sendLogs(log, "s3 stream statistics was not queried for 10 minutes, attempting to refresh s3 battles directly...");
				s3Downloader.downloadBattles(true);

//				if (lastUpdateTime != s3StreamStatistics.getLastUpdate()) {
//					logSender.sendLogs(log, "done");
//				} else {
//					logSender.sendLogs(log, "still no change! :(");
//				}
			}
		}

		final Long sendouUserId = NUMBER_PATTERN.matcher(sendouUserQueryString).matches()
			? Long.parseLong(sendouUserQueryString)
			: null;
		final Long tournamentId = NUMBER_PATTERN.matcher(tournamentString).matches()
			? Long.parseLong(tournamentString)
			: null;
		final boolean searchSendouQ = BOOLEAN_NUMBER_PATTERN.matcher(searchSendouQString).matches()
			&& "1".equals(searchSendouQString);

		sendouService.setSendouUserId(sendouUserId);
		sendouService.setTournamentId(tournamentId);
		sendouService.setSearchSendouQ(searchSendouQ);

		return s3StreamStatistics.getFinishedHtml();
	}

	@GetMapping(value = "/s3/fullscreen", produces = "text/html")
	public @ResponseBody String getS3FullscreenOverlay() {
		var html = "<html></html>";

		try (var is = this.getClass().getClassLoader().getResourceAsStream("html/s3/onstream-between-games-template.html")) {
			assert is != null;
			html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}

		return html;
	}

	@GetMapping("")
	public @ResponseBody String getOverlay() {
		return statistics.getFinishedHtml();
	}

	@GetMapping("/fullscreen")
	public @ResponseBody String getFullscreenOverlay() {
		return fullscreenExporter.getFinishedHtml();
	}

	@GetMapping(value = "/badges", produces = "text/html")
	public @ResponseBody String getBadgesOverlayHtml() {
		return badgeSender.getBadgesAsHtml();
	}

	@GetMapping(value = "/emotes", produces = "text/html")
	public @ResponseBody String getEmotesOverlayHtml() {
		return emoteSender.getEmotesAsHtml();
	}
}
