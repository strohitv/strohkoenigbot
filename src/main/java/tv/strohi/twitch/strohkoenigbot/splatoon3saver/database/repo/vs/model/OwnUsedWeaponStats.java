package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model;

import lombok.Data;

import javax.persistence.Cacheable;

@Cacheable(false)
@Data
public class OwnUsedWeaponStats {
	private final String modeName;
	private final long totalGames;
	private final long totalWins;
	private final long totalDefeats;
	private final double winRate;
	private final double defeatRate;

	public OwnUsedWeaponStats(String modeName, long totalGames, long totalWins, long totalDefeats) {
		this.modeName = modeName;
		this.totalGames = totalGames;
		this.totalWins = totalWins;
		this.totalDefeats = totalDefeats;

		this.winRate = 100.0 * totalWins / totalGames;
		this.defeatRate = 100.0 * totalDefeats / totalGames;
	}
}
