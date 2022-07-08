package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import com.github.twitch4j.pubsub.events.RewardRedeemedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.*;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Weapon;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2WeaponRequestRanking;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2WeaponRequestRankingRepository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Component
public class WeaponRequestRankingAction extends ChatAction {
	private static final String LAST_REQUESTER_NAME = "LastRequesterName";
	private static final String LAST_REQUESTER_ID = "LastRequesterId";

	// TODO needs to be reworked to be able to track several twitch accounts at once
	private String userId = null;
	private String userName = null;
	private Instant challengedAt = null;
	private boolean isStarted = false;
	private int winStreak = 0;
	private Splatoon2Match lastMatch = null;
	private long accountId = 0L;

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.ChannelPointReward);
	}

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private Splatoon2WeaponRequestRankingRepository splatoon2WeaponRequestRankingRepository;

	@Autowired
	public void setSplatoonWeaponRequestRankingRepository(Splatoon2WeaponRequestRankingRepository splatoon2WeaponRequestRankingRepository) {
		this.splatoon2WeaponRequestRankingRepository = splatoon2WeaponRequestRankingRepository;
	}

	private TwitchMessageSender twitchMessageSender;

	@Autowired
	public void setTwitchMessageSender(TwitchMessageSender twitchMessageSender) {
		this.twitchMessageSender = twitchMessageSender;
	}

	private Splatoon2WeaponRepository splatoon2WeaponRepository;

	@Autowired
	public void setSplatoonWeaponRepository(Splatoon2WeaponRepository splatoon2WeaponRepository) {
		this.splatoon2WeaponRepository = splatoon2WeaponRepository;
	}

	@Override
	public void execute(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (args.getReason() == TriggerReason.ChatMessage) {
			accountId = accountRepository.findByTwitchUserId((String) args.getArguments().getOrDefault(ArgumentKey.ChannelId, null))
					.filter(a -> a.getIsMainAccount() != null && a.getIsMainAccount())
					.map(Account::getId)
					.stream().findFirst()
					.orElse(0L);

			if (message.startsWith("!wr info")) {
				args.getReplySender().send("To make weapon requests more interesting, there's a ranking of which request made me get the biggest win streak! Try giving me a weapon which makes me win many games to reach first place! Type \"!wr list\" in chat to see the global ranking, type \"!wr me\" in chat to see your own ranking.");
			} else if (message.startsWith("!wr list")) {
				sendLeaderBoardToTwitch(args);
			} else if (message.startsWith("!wr me")) {
				sendAccountPlacementsToTwitch(args);
			} else if (message.startsWith("!wr rules")) {
				args.getReplySender().send("1. No requests while I'm playing with my Comp team. 2. No requests while I'm doing placements. 3. I'll play your weapon until I lose with it. 4. Banned weapons: Neo Sploosh & Custom Eliter 4k Scope. 5. One request per hour, one user can only do one request per stream. 6. You can request the same weapon as often as you want to.");
			} else if (message.startsWith("!wr")) {
				String channelName = (String) args.getArguments().getOrDefault(ArgumentKey.ChannelName, null);
				String channelId = (String) args.getArguments().getOrDefault(ArgumentKey.ChannelId, null);
				if (args.getUserId().equalsIgnoreCase(channelId)) {
					// Admin actions
					if (message.contains("start")
							&& !isStarted
							&& configurationRepository.findByConfigName(LAST_REQUESTER_ID).stream().map(Configuration::getConfigValue).findFirst().orElse(null) != null
							&& configurationRepository.findByConfigName(LAST_REQUESTER_NAME).stream().map(Configuration::getConfigValue).findFirst().orElse(null) != null) {
						twitchMessageSender.send(channelName, String.format("The weapon request for %s is now active. Let's see how far we can go! :0", configurationRepository.findByConfigName(LAST_REQUESTER_NAME).stream().map(Configuration::getConfigValue).findFirst().orElse("Unknown User")));
						start();
					} else if (message.contains("stop")) {
						twitchMessageSender.send(channelName, "A possibly running weapon request has been stopped.");
						stop();
					} else if (message.contains("reset force")) {
						stop();
						reset();
						twitchMessageSender.send(channelName, "Leaderboard got reset.");
					}
				}
			}
		} else {
			String reward = (String) args.getArguments().get(ArgumentKey.RewardName);

			if (reward != null && reward.toLowerCase().contains("weapon request")) {
				if (configurationRepository.findByConfigName(LAST_REQUESTER_ID).stream().findFirst().orElse(null) == null
						|| configurationRepository.findByConfigName(LAST_REQUESTER_NAME).stream().findFirst().orElse(null) == null) {
					configurationRepository.save(new Configuration(0, LAST_REQUESTER_ID, args.getUserId()));
					configurationRepository.save(new Configuration(0, LAST_REQUESTER_NAME, args.getUser()));

					RewardRedeemedEvent event = (RewardRedeemedEvent) args.getArguments().getOrDefault(ArgumentKey.Event, null);
					challengedAt = event != null ? event.getFiredAtInstant() : Instant.now();
					args.getReplySender().send(String.format("%s has redeemed a weapon request! Stroh will start soon if it doesn't break the rules.", args.getUser()));
				} else {
					args.getReplySender().send(String.format("Weapon request for %s can not be redeemed - there is another request pending... You will get your points back soon.", args.getUser()));
				}
			}
		}
	}

	private void start() {
		userId = configurationRepository.findByConfigName(LAST_REQUESTER_ID).stream().map(Configuration::getConfigValue).findFirst().orElse(null);
		userName = configurationRepository.findByConfigName(LAST_REQUESTER_NAME).stream().map(Configuration::getConfigValue).findFirst().orElse(null);

		winStreak = 0;
		isStarted = true;
	}

	public void addMatch(Splatoon2Match match) {
		if (!isStarted) return;

		lastMatch = match;
		Splatoon2Weapon weapon = splatoon2WeaponRepository.findById(lastMatch.getWeaponId()).orElse(null);
		if (weapon != null) {
			String weaponName = weapon.getName();
			if (match.getMatchResult() == Splatoon2MatchResult.Win) {
				winStreak += 1;

				twitchMessageSender.send("strohkoenig", String.format("Current win streak for the %s %s requested: %d matches", weaponName, userName, winStreak));
			} else {
				stop();
			}
		} else {
			twitchMessageSender.send("strohkoenig", "wtf didn't find weapon HUH??? This makes no sense wtf?");
		}
	}

	public void stop() {
		if (isStarted) {
			Splatoon2WeaponRequestRanking ranking = new Splatoon2WeaponRequestRanking();
			ranking.setChallengedAt(challengedAt);
			ranking.setTwitchId(userId);
			ranking.setTwitchName(userName);
			ranking.setWeaponId(lastMatch.getWeaponId());
			ranking.setWinStreak(winStreak);
			ranking.setAccountId(accountId);

			ranking = splatoon2WeaponRequestRankingRepository.save(ranking);
			final long rankingId = ranking.getId();

			List<Splatoon2WeaponRequestRanking> allRankings = splatoon2WeaponRequestRankingRepository.findAllByAccountIdOrderByWinStreakDescChallengedAtAsc(accountId);
			int position = allRankings.indexOf(allRankings.stream().filter(r -> r.getId() == rankingId).findFirst().orElse(null));

			Splatoon2Weapon weapon = splatoon2WeaponRepository.findById(lastMatch.getWeaponId()).orElse(null);
			if (weapon != null) {
				String weaponName = weapon.getName();
				if (winStreak == 0) {
					twitchMessageSender.send("strohkoenig", String.format("Oh no! I couldn't win a single game with the %s %s requested! strohk2HuhFree Your request reached position %d out of %d registered attempts. Use \"!wr me\" to see your positions so far.", weaponName, userName, position, allRankings.size()));
				} else if (winStreak == 1) {
					twitchMessageSender.send("strohkoenig", String.format("I reached a win streak of %d game with the %s %s requested! Your request reached position %d out of %d registered attempts. Use \"!wr me\" to see your positions so far.", winStreak, weaponName, userName, position, allRankings.size()));
				} else {
					twitchMessageSender.send("strohkoenig", String.format("I reached a win streak of %d games with the %s %s requested! Your request reached position %d out of %d registered attempts. Use \"!wr me\" to see your positions so far.", winStreak, weaponName, userName, position, allRankings.size()));
				}
			} else {
				twitchMessageSender.send("strohkoenig", "wtf didn't find weapon HUH???");
			}
		}

		userId = null;
		userName = null;

		winStreak = 0;
		isStarted = false;
		challengedAt = null;

		lastMatch = null;

		configurationRepository.findByConfigName(LAST_REQUESTER_ID).forEach(configurationRepository::delete);
		configurationRepository.findByConfigName(LAST_REQUESTER_NAME).forEach(configurationRepository::delete);
	}

	private void reset() {
		splatoon2WeaponRequestRankingRepository.deleteAll();
	}

	private void sendLeaderBoardToTwitch(ActionArgs args) {
		List<Splatoon2WeaponRequestRanking> allRankings = splatoon2WeaponRequestRankingRepository.findAllByAccountIdOrderByWinStreakDescChallengedAtAsc(accountId);
		List<Splatoon2Weapon> allWeapons = splatoon2WeaponRepository.findAll();

		if (allRankings.size() > 0) {
			StringBuilder firstFewPlaces = new StringBuilder();
			int current = 1;

			for (Splatoon2WeaponRequestRanking ranking : allRankings) {
				String message = String.format("%d: %s from %s (%d wins)",
						current,
						allWeapons.stream().filter(w -> w.getId() == ranking.getWeaponId()).map(Splatoon2Weapon::getName).findFirst().orElse("Unknown Weapon"),
						ranking.getTwitchName(),
						ranking.getWinStreak());

				current++;

				if (firstFewPlaces.length() + 3 + message.length() > 500) {
					break;
				}

				if (firstFewPlaces.length() > 0) {
					firstFewPlaces.append(" - ");
				} else {
					firstFewPlaces.append("Top positions on the leaderboard: ");
				}

				firstFewPlaces.append(message);
			}

			args.getReplySender().send(firstFewPlaces.toString());
		} else {
			args.getReplySender().send("There's no one on the leaderboard yet. Be the first to request a weapon!");
		}
	}

	private void sendAccountPlacementsToTwitch(ActionArgs args) {
		List<Splatoon2WeaponRequestRanking> allRankings = splatoon2WeaponRequestRankingRepository.findAllByAccountIdOrderByWinStreakDescChallengedAtAsc(accountId);
		List<Splatoon2WeaponRequestRanking> accountRankings = splatoon2WeaponRequestRankingRepository.findAllByAccountIdAndTwitchIdOrderByWinStreakDescChallengedAtAsc(accountId, args.getUserId());
		List<Splatoon2Weapon> allWeapons = splatoon2WeaponRepository.findAll();

		if (accountRankings.size() > 0) {
			StringBuilder accountPlacements = new StringBuilder();

			for (Splatoon2WeaponRequestRanking ranking : accountRankings) {
				String message = String.format("%d: %s (%d wins)",
						allRankings.indexOf(allRankings.stream().filter(ar -> ar.getId() == ranking.getId()).findFirst().orElse(null)) + 1,
						allWeapons.stream().filter(w -> w.getId() == ranking.getWeaponId()).map(Splatoon2Weapon::getName).findFirst().orElse("Unknown Weapon"),
						ranking.getWinStreak());

				if (accountPlacements.length() + 3 + message.length() > 500) {
					break;
				}

				if (accountPlacements.length() > 0) {
					accountPlacements.append(" - ");
				} else {
					accountPlacements.append("Your positions on the leaderboard: ");
				}

				accountPlacements.append(message);
			}

			args.getReplySender().send(accountPlacements.toString());
		} else {
			args.getReplySender().send("You're not on the leaderboard yet. Try requesting a weapon first by using your channel points!");
		}
	}
}
