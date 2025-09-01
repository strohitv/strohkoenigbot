package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeaponsResult implements Serializable {
	private WeaponsResult.Data data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Data implements Serializable {
		private WeaponNodes weaponRecords;
		private WeaponNodes allWeapons;
		private WeaponNodes ownedWeapons;

		private PlayHistory playHistory;

		private WeaponCategoryNodes weaponCategories;
		private SubWeaponNodes subWeapons;
		private SpecialWeaponNodes specialWeapons;

		private BestNineRanking bestNineRanking;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponNodes implements Serializable {
		private Weapon[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponCategoryNodes implements Serializable {
		private WeaponCategory[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SubWeaponNodes implements Serializable {
		private WeaponDetail[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SpecialWeaponNodes implements Serializable {
		private WeaponDetail[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PlayHistory implements Serializable {
		private Double maxWeaponPowerTotal;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BestNineRanking {
		public CurrentSeason currentSeason;
		public CurrentPlayer currentPlayer;
		public PastSeasons pastSeasons;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PastSeasons {
		public List<PastSeasonEdge> edges;
		public PageInfo pageInfo;
	}
}
