package tv.strohi.twitch.strohkoenigbot.chatbot.actions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ArgumentKey;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ChatAction;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.TriggerReason;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.util.TwitchDiscordMessageSender;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth.S3Authenticator;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.authentication.AuthLinkCreator;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordAccountLoader;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RegisterSplatoon3AccountAction extends ChatAction {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private AccountRepository accountRepository;

	@Autowired
	public void setAccountRepository(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	private DiscordAccountLoader accountLoader;

	@Autowired
	public void setAccountLoader(DiscordAccountLoader accountLoader) {
		this.accountLoader = accountLoader;
	}

	// TODO later
//	private StatsExporter statsExporter;
//
//	@Autowired
//	public void setStatsExporter(StatsExporter statsExporter) {
//		this.statsExporter = statsExporter;
//	}

	private final AuthLinkCreator authLinkCreator = new AuthLinkCreator();
	private final S3Authenticator authenticator;
	private final Map<Long, Tuple<AuthLinkCreator.AuthParams, Instant>> paramsMap = new HashMap<>();

	@Override
	public EnumSet<TriggerReason> getCauses() {
		return EnumSet.of(TriggerReason.DiscordPrivateMessage);
	}

	@Override
	protected void execute(ActionArgs args) {
		TwitchDiscordMessageSender sender = args.getReplySender();

		String message = (String) args.getArguments().getOrDefault(ArgumentKey.Message, null);
		if (message == null) {
			return;
		}

		message = message.trim();

		if (message.toLowerCase().startsWith("!splatoon3")) {
			message = message.substring("!splatoon3".length()).trim();

			Account account = accountLoader.loadAccount(Long.parseLong(args.getUserId()));

			if (message.toLowerCase().startsWith("register")) {
				logger.info("Register splatoon token has been called");

				AuthLinkCreator.AuthParams params = authLinkCreator.generateAuthenticationParams();
				String authUrl = authLinkCreator.buildAuthUrl(params).toString();

				paramsMap.put(Long.parseLong(args.getUserId()), new Tuple<>(params, Instant.now().plus(5, ChronoUnit.MINUTES)));

				sender.send("**There are two ways to connect your splatoon account with this bot**:\n\n" +
						"1. By using the token generation method from frozenpandaman & NexusMine <https://github.com/frozenpandaman/splatnet2statink/>.\n" +
						"	This method involves calling their servers. If you don't want non-Nintendo servers to be involved in the cookie generation, please use the other method.\n\n" +
						"	Steps to use this method:\n" +
						"	1. Navigate to this URL in your browser:\n" +
						"" + authUrl + "\n" +
						"	2. Log in to your Nintendo account.\n"+
						"	3. do a **right click** on the \"Select this person\" button and copy the link address.\n"+
						"	4. send me a direct message with the copied address **within five minutes** in this form:" +
						"```!splatoon3 link COPIED_ADDRESS```" +
						"	For example: `!splatoon3 link npf71b963...` (link is much longer)\n\n" +
						"2. **THIS IS NOT POSSIBLE YET** By using mitmproxy, instructions can be found here: <https://github.com/frozenpandaman/splatnet2statink/wiki/mitmproxy-instructions>.\n" +
						"	1. After copying the cookie (xxxxx) in Step 7, send me the cookie via direct message.\n" +
						"	2. Send the message in this form:" +
						"```!splatoon3 cookie COPIED_COOKIE```" +
						"	For example: `!splatoon3 cookie ac2f44d4a...` (cookie is much longer)");
			} else if (message.toLowerCase().startsWith("link") && !(message = message.substring("link".length()).trim()).isBlank()) {
				Tuple<AuthLinkCreator.AuthParams, Instant> params = paramsMap.getOrDefault(account.getDiscordId(), null);

				if (params != null) {
					if (Instant.now().isBefore(params.second)) {
						try {
							account = generateAndStoreSessionToken(account, params.first, message);
							// TODO later
//							statsExporter.refreshStatsForAccount(account);

							sender.send("I successfully connected your account.");
						} catch (Exception ex) {
							logger.error(ex);
							sender.send("I could not connect your account. Please make sure you copied the correct link.");
						}
					} else {
						sender.send("You didn't send the link in time. Please try again using **!splatoon3 register**.");
					}
				} else {
					sender.send("Please generate a link by using **!splatoon3 register** first.");
				}
			} else if (message.toLowerCase().startsWith("cookie") && !(message = message.substring("cookie".length()).trim()).isBlank()) {
				sender.send("This action is not supported yet.");
//				account.setGTokenSplatoon3(message);
//				account.setBulletTokenSplatoon3(Instant.now().plus(10, ChronoUnit.MINUTES));
//				account.setSessionTokenSplatoon3(null);
//				account.setSplatoonNickname(args.getUser());
//				account.setRateLimitNumberSplatoon3(new Random().nextInt(30));
//
//				account = accountRepository.save(account);
//
//				statsExporter.refreshStatsForAccount(account);
//
//				sender.send("I successfully connected your account.");
			} else if (message.toLowerCase().startsWith("enable") && account.getSplatoonSessionToken() != null && !account.getSplatoonSessionToken().isBlank()) {
				account.setEnableSplatoon3(true);
				accountRepository.save(account);
				sender.send("Splatoon 3 was enabled for your account.");
			} else if (message.toLowerCase().startsWith("disable")) {
				account.setEnableSplatoon3(false);
				accountRepository.save(account);
				sender.send("Splatoon 3 was disabled for your account.");
			} else if (message.toLowerCase().startsWith("delete")) {
				account.setGTokenSplatoon3(null);
				account.setBulletTokenSplatoon3(null);
				account.setSplatoonSessionToken(null);
				account.setSplatoonNickname(args.getUser());

				accountRepository.save(account);

				sender.send("I successfully deleted the splatoon cookie for your account.");
			} else {
				sender.send("Allowed commands:\n" +
						"    - !splatoon3 register\n" +
						"    - !splatoon3 link\n" +
						"    - !splatoon3 cookie\n" +
						"    - !splatoon3 enable\n" +
						"    - !splatoon3 disable\n" +
						"    - !splatoon3 delete");
			}
		}
	}

	@NotNull
	public Account generateAndStoreSessionToken(Account account, AuthLinkCreator.AuthParams params, String redirectLink) {
		URI link = URI.create(redirectLink);
		String session_token_code = getQueryMap(link.getFragment()).get("session_token_code");

		account.setSplatoonSessionToken(authenticator.getSessionToken("71b963c1b7b6d119", session_token_code, params.getCodeVerifier()));
		account.setGTokenSplatoon3(null);
		account.setBulletTokenSplatoon3(null);
		account.setSplatoonNickname(null);

		account = accountRepository.save(account);
		return account;
	}

	public Map<String, String> getQueryMap(String query) {
		String[] params = query.split("&");

		Map<String, String> map = new HashMap<>();

		for (String param : params) {
			String name = param.split("=")[0];
			String value = param.split("=")[1];
			map.put(name, value);
		}

		return map;
	}

	@Getter
	@Setter
	@AllArgsConstructor
	private static class Tuple <T1, T2> {
		private T1 first;
		private T2 second;
	}
}
