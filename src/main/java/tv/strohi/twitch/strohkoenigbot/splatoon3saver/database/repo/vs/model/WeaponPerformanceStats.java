package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model;

import lombok.Data;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsWeapon;

import javax.persistence.Cacheable;

@Cacheable(false)
@Data
public class WeaponPerformanceStats {
	private final Splatoon3VsWeapon weapon;
	private final boolean isMyTeam;
	private final int totalGames;
	private final int totalWins;
	private final int totalDefeats;
	private final double winRate;
	private final double defeatRate;

	public WeaponPerformanceStats(Splatoon3VsWeapon weapon, boolean isMyTeam, int totalGames, int totalWins, int totalDefeats) {
		this.weapon = weapon;
		this.isMyTeam = isMyTeam;
		this.totalGames = totalGames;
		this.totalWins = totalWins;
		this.totalDefeats = totalDefeats;

		this.winRate = 100.0 * totalWins / totalGames;
		this.defeatRate = 100.0 * totalDefeats / totalGames;
	}
}
