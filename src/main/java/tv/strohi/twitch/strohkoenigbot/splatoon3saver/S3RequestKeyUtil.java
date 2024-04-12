package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3RequestKey;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.Splatoon3RequestKeyRepository;

@Component
@RequiredArgsConstructor
public class S3RequestKeyUtil {
	private final Splatoon3RequestKeyRepository requestKeyRepository;

	public String load(S3RequestKey requestKey) {
		return requestKeyRepository.findByQueryName(requestKey.getQueryName())
			.map(Splatoon3RequestKey::getQueryHash)
			.orElse(requestKey.getKey());
	}
}
