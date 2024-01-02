package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Badge;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Nameplate;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Player;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3BadgeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3NameplateRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3PlayerRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Badge;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Nameplate;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Base64;

@Service
@Transactional
@RequiredArgsConstructor
public class Splatoon3GeneralService {
	private final ImageService imageService;

	private final Splatoon3PlayerRepository playerRepository;
	private final Splatoon3NameplateRepository nameplateRepository;
	private final Splatoon3BadgeRepository badgeRepository;

	@Transactional
	public Splatoon3Player ensurePlayerExists(String base64PlayerId) {
		var decodedPlayerId = Arrays.stream(new String(Base64.getDecoder().decode(base64PlayerId)).split(":"))
			.reduce((a, b) -> b)
			.orElse(base64PlayerId);

		return playerRepository.findByApiPrefixedId(decodedPlayerId)
			.orElseGet(() -> playerRepository.save(
				Splatoon3Player.builder()
					.apiId(decodedPlayerId.substring(2))
					.apiPrefixedId(decodedPlayerId)
					.build()));
	}

	@Transactional
	public Splatoon3Nameplate ensureNameplateExists(Nameplate nameplate, boolean myself) {
		var s3Nameplate = nameplateRepository.findByApiId(nameplate.getBackground().getId())
			.orElseGet(() -> nameplateRepository.save(
				Splatoon3Nameplate.builder()
					.apiId(nameplate.getBackground().getId())
					.image(imageService.ensureExists(nameplate.getBackground().getImage().getUrl()))
					.owned(false)
					.textColorR(nameplate.getBackground().getTextColor().getR())
					.textColorG(nameplate.getBackground().getTextColor().getG())
					.textColorB(nameplate.getBackground().getTextColor().getB())
					.textColorA(nameplate.getBackground().getTextColor().getA())
					.build()));

		if (myself && !s3Nameplate.getOwned()) {
			return nameplateRepository.save(s3Nameplate.toBuilder()
				.owned(true)
				.build());
		} else {
			return s3Nameplate;
		}
	}

	@Transactional
	public Splatoon3Badge ensureBadgeExists(Badge badge, boolean myself) {
		var s3Badge = badgeRepository.findByApiId(badge.getId())
			.orElseGet(() -> badgeRepository.save(
				Splatoon3Badge.builder()
					.apiId(badge.getId())
					.image(imageService.ensureExists(badge.getImage().getUrl()))
					.owned(false)
					.build()));

		if (myself && !s3Badge.getOwned()) {
			return badgeRepository.save(s3Badge.toBuilder()
				.owned(true)
				.build());
		} else {
			return s3Badge;
		}
	}
}
