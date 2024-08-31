package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.S3CookieHandler;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.S3RequestSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebViewVersionLoader {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());
	private final S3RequestSender s3RequestSender = new S3RequestSender();

	public String refreshWebViewVersion(String gToken) {
		var client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(120))
				.version(HttpClient.Version.HTTP_2)
				.cookieHandler(new S3CookieHandler(gToken, true))
				.build();

		String homepageAddress = "https://api.lp1.av5ja.srv.nintendo.net";

		URI homePageUri = URI.create(homepageAddress);

		HttpRequest homePageRequest = HttpRequest.newBuilder()
				.GET()
				.uri(homePageUri)
				.setHeader("Accept", "*/*")
				.setHeader("DNT", "1")
				.setHeader("X-AppColorScheme", "DARK")
				.setHeader("X-Requested-With", "com.nintendo.znca")
				.setHeader("Sec-Fetch-Site", "none")
				.setHeader("Sec-Fetch-Mode", "navigate")
				.setHeader("Sec-Fetch-User", "?1")
				.setHeader("Sec-Fetch-Dest", "document")
				.setHeader("Accept-Language", "en-US")
				.setHeader("Accept-Encoding", "gzip,deflate,br")
//			.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
				.setHeader("User-Agent", RequestSender.getDefaultUserAgent())
				.build();

		String webViewVersion = null;

		try {
			String homepageHtml = s3RequestSender.sendRequestAndParseGzippedJson(client, homePageRequest);
			logger.debug(homepageHtml);

			if (homepageHtml != null) {
				Document doc = Jsoup.parse(homepageHtml);
				String mainJsPath = doc.select("script").stream()
						.filter(script -> script.attributes().asList().stream().anyMatch(a ->
								"src".equals(a.getKey())
										&& a.getValue() != null
										&& a.getValue().contains("main.") && a.getValue().contains(".js")))
						.map(script -> script.attributes().get("src"))
						.findFirst()
						.orElse(null);

				if (mainJsPath != null) {
					String mainJsAddress = homepageAddress + mainJsPath;

					URI mainJsUri = URI.create(mainJsAddress);

					HttpRequest mainJsRequest = HttpRequest.newBuilder()
							.GET()
							.uri(mainJsUri)
							.setHeader("Accept", "*/*")
							.setHeader("Referer", homepageAddress)
							.setHeader("Sec-Fetch-Site", "same-origin")
							.setHeader("Sec-Fetch-Mode", "no-cors")
							.setHeader("Sec-Fetch-Dest", "script")
							.setHeader("Accept-Language", "en-US")
							.setHeader("Accept-Encoding", "gzip,deflate,br")
//			.setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36")
							.setHeader("User-Agent", RequestSender.getDefaultUserAgent())
							.build();

					String mainJsContent = s3RequestSender.sendRequestAndParseGzippedJson(client, mainJsRequest);
					logger.debug(mainJsContent);

					if (mainJsContent != null) {
						Matcher mainJsMatcher = Pattern
								.compile("\\b(?<revision>[\\da-f]{40})\\b\\S*?void 0\\S*?\"revision_info_not_set\"}`,.*?=`(?<version>\\d+\\.\\d+\\.\\d+)-")
								.matcher(mainJsContent);

						if (mainJsMatcher.find()) {
							String revision = mainJsMatcher.group("revision");
							String version = mainJsMatcher.group("version");

							webViewVersion = String.format("%s-%s", version, revision.substring(0,8));
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("couldn't receive web view version");
			logger.error(e);
		}

		return webViewVersion;
	}
}
