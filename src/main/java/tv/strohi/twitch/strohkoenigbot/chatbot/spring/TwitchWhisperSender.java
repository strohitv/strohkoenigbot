package tv.strohi.twitch.strohkoenigbot.chatbot.spring;

import ch.qos.logback.core.CoreConstants;
import com.github.twitch4j.helix.domain.UserList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchMessageSentHistory;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchMessageSentHistoryRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TwitchWhisperSender {
	private final TwitchBotClient botClient;
	private final TwitchAuthRepository authRepository;
	private final TwitchMessageSentHistoryRepository twitchMessageSentHistoryRepository;

	@Autowired
	public TwitchWhisperSender(@Qualifier("botClient") TwitchBotClient botClient, TwitchAuthRepository authRepository, TwitchMessageSentHistoryRepository twitchMessageSentHistoryRepository) {
		this.botClient = botClient;
		this.authRepository = authRepository;
		this.twitchMessageSentHistoryRepository = twitchMessageSentHistoryRepository;
	}

	public boolean sendMessageToChannelWithId(String channelId, String message) {
		boolean result = false;

		List<TwitchAuth> auth = authRepository.findByIsMain(false);
		TwitchAuth main = authRepository.findByIsMain(true).stream().findFirst().orElse(null);

		if (auth.size() >= 1) {
			UserList users = botClient.getClient().getHelix().getUsers(auth.get(0).getToken(), Collections.singletonList(channelId), null).execute();

			List<TwitchMessageSentHistory> sentMessages = twitchMessageSentHistoryRepository.findBySentAtIsAfter(Instant.now().minus(1, ChronoUnit.DAYS));
			Map<String, List<TwitchMessageSentHistory>> map = sentMessages.stream()
					.filter(m -> !m.getUserId().equals(main != null ? main.getChannelId() : null))
					.collect(Collectors.groupingBy(TwitchMessageSentHistory::getUserId));

			if (main != null && !channelId.equals(main.getChannelId())
					|| users.getUsers().size() > 0 && (map.keySet().size() < 39 || map.keySet().size() == 39 && map.containsKey(channelId))) {
				TwitchMessageSentHistory sentHistory = new TwitchMessageSentHistory();
				sentHistory.setUserId(channelId);
				sentHistory.setSentAt(Instant.now());
				sentHistory.setMessage(message.substring(0, Math.min(message.length(), 2000)));

				twitchMessageSentHistoryRepository.save(sentHistory);

				// todo: switch to discord pn
				botClient.getClient().getChat().sendPrivateMessage(users.getUsers().get(0).getLogin(), message);sdvsdv
				result = true;
			}
		}

		return result;
	}

	@Scheduled(fixedDelay = CoreConstants.MILLIS_IN_ONE_DAY)
	public void clearOldEntries() {
		List<TwitchMessageSentHistory> sentMessages = twitchMessageSentHistoryRepository.findBySentAtBefore(Instant.now().minus(1, ChronoUnit.DAYS));
		if (sentMessages.size() > 0) {
			twitchMessageSentHistoryRepository.deleteAll(sentMessages);
		}
	}
}
