package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;

@AllArgsConstructor
@Getter
public enum S3RequestKey {
	Home("HomeQuery", "51fc56bbf006caf37728914aa8bc0e2c86a80cf195b4d4027d6822a3623098a8", "/"),
	History("HistoryRecordQuery", "0a62c0152f27c4218cf6c87523377521c2cff76a4ef0373f2da3300079bf0388", "/history_record/summary"),
	Catalog("CatalogQuery", "40b62e4734f22a6009f1951fc1d03366b14a70833cb96a9a46c0e9b7043c67ef", "/catalog_record"),

	Latest("LatestBattleHistoriesQuery", "b24d22fd6cb251c515c2b90044039698aa27bc1fab15801d83014d919cd45780", "/history/latest"),
	Regular("RegularBattleHistoriesQuery", "2fe6ea7a2de1d6a888b7bd3dbeb6acc8e3246f055ca39b80c4531bbcd0727bba", "/history/regular"),
	Anarchy("BankaraBattleHistoriesQuery", "9863ea4744730743268e2940396e21b891104ed40e2286789f05100b45a0b0fd", "/history/bankara"),
	XRank("XBattleHistoriesQuery", "eb5996a12705c2e94813a62e05c0dc419aad2811b8d49d53e5732290105559cb", "/history/xmatch"),
	Challenge("EventBattleHistoriesQuery", "e47f9aac5599f75c842335ef0ab8f4c640e8bf2afe588a3b1d4b480ee79198ac", "/history/event"),
	Private("PrivateBattleHistoriesQuery", "fef94f39b9eeac6b2fac4de43bc0442c16a9f2df95f4d367dd8a79d7c5ed5ce7", "/history/private"),
	GameDetail("VsHistoryDetailQuery", "f893e1ddcfb8a4fd645fd75ced173f18b2750e5cfba41d2669b9814f6ceaec46", "/history/detail/VnNIaXN0b3J5RGV0YWlsLXUtYWVrcHRqYmd6N3hvdmd3emtvbW06QkFOS0FSQToyMDIzMDgyN1QyMTQ5NTBfYWY0MmRjYTItODY2ZC00YTY1LTg5ZDUtY2U3OGY1Y2U4YmQ4"),

	Salmon("CoopHistoryQuery", "0f8c33970a425683bb1bdecca50a0ca4fb3c3641c0b2a1237aedfde9c0cb2b8f", "/coop"),
	SalmonDetail("CoopHistoryDetailQuery", "42262d241291d7324649e21413b29da88c0314387d8fdf5f6637a2d9d29954ae", "/coop/Q29vcEhpc3RvcnlEZXRhaWwtdS1hZWtwdGpiZ3o3eG92Z3d6a29tbToyMDIzMDkwM1QyMTQ4NDlfNTI4OWJjY2YtYjUxYS00Yzg4LWJiMDktNjEzMzZlOTA3OGVl"),

	SplatNetShop("GesotownQuery", "d6f94d4c05a111957bcd65f8649d628b02bf32d81f26f1d5b56eaef438e55bab", "/gesotown"),
	OwnedWeaponsAndGear("myOutfitCommonDataEquipmentsQuery", "45a4c343d973864f7bb9e9efac404182be1d48cf2181619505e9b7cd3b56a6e8", "/my_outfits/create"),
	Weapons("WeaponRecordQuery", "974fad8a1275b415c3386aa212b07eddc3f6582686e4fef286ec4043cdf17135", "/weapon_record"),

	XRankStats("XRankingQuery", "a5331ed228dbf2e904168efe166964e2be2b00460c578eee49fc0bc58b4b899c", "/x_ranking"),

	XRankLeaderboardNextPageZones("DetailTabViewXRankingArRefetchQuery", "0dc7b908c6d7ad925157a7fa60915523dab4613e6902f8b3359ae96be1ba175f", "/x_ranking/WFJhbmtpbmdTZWFzb24tYTo4/ar"),
	XRankLeaderboardNextPageTower("DetailTabViewXRankingLfRefetchQuery", "ca55206629f2c9fab38d74e49dda3c5452a83dd02a5a7612a2520a1fc77ae228", "/x_ranking/WFJhbmtpbmdTZWFzb24tYTo4/lf"),
	XRankLeaderboardNextPageRainmaker("DetailTabViewXRankingGlRefetchQuery", "6ab0299d827378d2cae1e608d349168cd4db21dd11164c542d405ed689c9f622", "/x_ranking/WFJhbmtpbmdTZWFzb24tYTo4/gl"),
	XRankLeaderboardNextPageClams("DetailTabViewXRankingClRefetchQuery", "485e5decc718feeccf6dffddfe572455198fdd373c639d68744ee81507df1a48", "/x_ranking/WFJhbmtpbmdTZWFzb24tYTo4/cl"),

	RotationSchedules("StageScheduleQuery", "d49fb6adffe15e3e43ca1167397debfc580eede3ad2232d7e32062bc5487e7eb", "/schedule/bankara");

	@Getter
	private final static EnumSet<S3RequestKey> onlineBattles = EnumSet.of(Regular, Anarchy, XRank, Challenge, Private); // EnumSet.of(Latest, Regular, Anarchy, Private); //

	@Getter
	private final static EnumSet<S3RequestKey> xRankLeaderBoardQueries = EnumSet.of(XRankLeaderboardNextPageZones, XRankLeaderboardNextPageTower, XRankLeaderboardNextPageRainmaker, XRankLeaderboardNextPageClams);

	private final String queryName;
	private final String key;
	private final String path;
}
