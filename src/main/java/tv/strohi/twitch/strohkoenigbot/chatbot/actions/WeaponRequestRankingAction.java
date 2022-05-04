package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.IChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMatchResult;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.TwitchAuthRepository;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonWeaponRepository;

import java.util.EnumSet;

@Component
public class WeaponRequestRankingAction implements IChatAction {
	private static final String LAST_REQUESTER_NAME = "LastRequesterName";
	private static final String LAST_REQUESTER_ID = "LastRequesterId";

	private String userId = null;
	private String userName = null;
	private boolean isStarted = false;
	private int winStreak = 0;
	private SplatoonMatch lastMatch = null;

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.ChatMessage, TriggerReason.ChannelPointReward);
	}

	private ConfigurationRepository configurationRepository;

	@Autowired
	public void setConfigurationRepository(ConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	private TwitchAuthRepository twitchAuthRepository;

	@Autowired
	public void setTwitchAuthRepository(TwitchAuthRepository twitchAuthRepository) {
		this.twitchAuthRepository = twitchAuthRepository;
	}

	private TwitchMessageSender twitchMessageSender;

	@Autowired
	public void setTwitchMessageSender(TwitchMessageSender twitchMessageSender) {
		this.twitchMessageSender = twitchMessageSender;
	}

	private SplatoonWeaponRepository splatoonWeaponRepository;

	@Autowired
	public void setSplatoonWeaponRepository(SplatoonWeaponRepository splatoonWeaponRepository) {
		this.splatoonWeaponRepository = splatoonWeaponRepository;
	}

	@Override
	public void run(ActionArgs args) {
		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.toLowerCase().trim();

		if (args.getReason() == TriggerReason.ChatMessage) {
			if (message.startsWith("!wr info")) {
				args.getReplySender().send("To make weapon requests more interesting, there's a ranking of which request made me get the biggest win streak! Try giving me a weapon which makes me win many games to reach first place! Type \"!wr list\" in chat to see the ranking.");
			} else if (message.startsWith("!wr list")) {
				args.getReplySender().send("Ranking hasn't started yet and is still todo, sorry...");
			} else if (message.startsWith("!wr rules")) {
				args.getReplySender().send("1. No requests while I'm playing with my Comp team. 2. No requests while I'm doing placements. 3. I'll play your weapon until I lose with it. 4. Banned weapons: Neo Sploosh & Custom Eliter 4k Scope. 5. One request per hour, one user can only do one request per stream.");
			} else if (message.startsWith("!wr")) {
				TwitchAuth mainAccount = twitchAuthRepository.findByIsMain(true).stream().findFirst().orElse(null);
				if (mainAccount != null && args.getUser().equalsIgnoreCase(mainAccount.getChannelId())) {
					// Admin actions
					if (message.contains("start")) {
						twitchMessageSender.send(mainAccount.getUsername(), String.format("The weapon request for %s now active. Let's see how far we can go! :0", args.getUser()));
						start();
					} else if (message.contains("stop")) {
						twitchMessageSender.send(mainAccount.getUsername(), String.format("The weapon request for %s was stopped.", args.getUser()));
						stop();
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
					args.getReplySender().send(String.format("%s has redeemed a weapon request! It'll start soon as long as it doesn't break the rules.", args.getUser()));
				} else {
					args.getReplySender().send(String.format("Weapon request for %s cannot be redeemed - there's another request pending... You will get your points back soon.", args.getUser()));
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

	public void addMatch(SplatoonMatch match) {
		if (!isStarted) return;

		lastMatch = match;
		String weaponName = splatoonWeaponRepository.getById(lastMatch.getWeaponId()).getName();
		if (match.getMatchResult() == SplatoonMatchResult.Win) {
			winStreak += 1;

			twitchMessageSender.send("strohkoenig", String.format("Current win streak for the %s %s requested: %d matches", weaponName, userName, winStreak));
		} else {
			if (winStreak == 0) {
				twitchMessageSender.send("strohkoenig", String.format("Oh no! I couldn't win a single game with the %s %s requested! strohk2HuhFree", weaponName, userName));
			} else {
				twitchMessageSender.send("strohkoenig", String.format("I reached a win streak of %d games with the %s %s requested!", winStreak, weaponName, userName));
			}

			stop();
		}
	}

	private void stop() {
		// TODO persistence of the rank list

		userId = null;
		userName = null;

		winStreak = 0;
		isStarted = false;

		lastMatch = null;

		configurationRepository.findByConfigName(LAST_REQUESTER_ID).forEach(configurationRepository::delete);
		configurationRepository.findByConfigName(LAST_REQUESTER_NAME).forEach(configurationRepository::delete);
	}
}
