package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.repository.AccountRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.S3CookieHandler;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth.S3AuthenticationData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth.S3Authenticator;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.S3RequestSender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

@Component
@RequiredArgsConstructor
public class S3ApiQuerySender {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final S3RequestSender s3RequestSender;
	private final AccountRepository accountRepository;

	private final LogSender logSender;

	private final S3Authenticator authenticator = new S3Authenticator();

	public String queryS3Api(Account account, String actionHash) {
		return queryS3Api(account, actionHash, null);
	}

	public String queryS3Api(Account account, String actionHash, String matchId) {
		String result = doRequest(account.getGTokenSplatoon3(), account.getBulletTokenSplatoon3(), actionHash, matchId);

		if (result == null) {
			logSender.sendLogs(logger, "Didn't receive a result, retrying after refreshing tokens...");

			// Tokens might be outdated -> retry once with refreshed Tokens
			S3AuthenticationData authenticationData = authenticator.refreshAccess(account.getSplatoonSessionToken());
			account.setGTokenSplatoon3(authenticationData.getGToken());
			account.setBulletTokenSplatoon3(authenticationData.getBulletToken());

			account = accountRepository.save(account);
			result = doRequest(account.getGTokenSplatoon3(), account.getBulletTokenSplatoon3(), actionHash, matchId);
			logSender.sendLogs(logger, String.format("is result null again? %b", result != null));
		}

		return result;
	}

	private String doRequest(String gToken, String bulletToken, String actionHash, String matchId) {
		String body = String.format("{\"variables\":{},\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"%s\"}}}", actionHash);

		if (matchId != null) {
			if (S3RequestKey.GameDetail.getKey().equals(actionHash)) {
				body = String.format("{\"variables\":{\"vsResultId\":\"%s\"},\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"%s\"}}}", matchId, actionHash);
			} else {
				// salmon run
				body = String.format("{\"variables\":{\"coopHistoryDetailId\":\"%s\"},\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"%s\"}}}", matchId, actionHash);
			}
		}

		String address = "https://api.lp1.av5ja.srv.nintendo.net/api/graphql";

		URI uri = URI.create(address);

		HttpRequest request = HttpRequest.newBuilder()
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.uri(uri)
				.setHeader("Authorization", String.format("Bearer %s", bulletToken))
				.setHeader("Accept-Language", "en-US")
				.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
				.setHeader("X-Web-View-Ver", "1.0.0-d3a90678")
				.setHeader("Content-Type", "application/json")
				.setHeader("Accept", "*/*")
				.setHeader("Origin", "https://api.lp1.av5ja.srv.nintendo.net")
				.setHeader("X-Requested-With", "com.nintendo.znca")
				.setHeader("Referer", "https://api.lp1.av5ja.srv.nintendo.net/?lang=en-US&na_country=US&na_lang=en-US")
				.setHeader("Accept-Encoding", "gzip, deflate")
				.build();

		HttpClient client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_2)
				.cookieHandler(new S3CookieHandler(gToken, false))
				.build();

		return s3RequestSender.sendRequestAndParseGzippedJson(client, request);
	}
}
