package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;

@AllArgsConstructor
@Getter
public enum S3RequestKey {
	Home("7dcc64ea27a08e70919893a0d3f708710", "/"),
	History("67921048c4af8e2b201a12f13ad0ddae", "/history_record/summary"),
	Catalog("ff12098bad4989a813201b00ff22ac4e", "/catalog_record"),

	Latest("6b74405ca9b43ee77eb8c327c3c1a317", "/history/latest"),
	Regular("4c95233c8d55e7c8cc23aae06109a2e8", "/history/regular"),
	Anarchy("92b56403c0d9b1e63566ec98fef52eb3", "/history/bankara"),
	XRank("94711fc9f95dd78fc640909f02d09215", "/history/xmatch"),
	Challenge("5650c7abd4e377e74f95e30031864208", "/history/event"),
	Private("89bc61012dcf170d9253f406ebebee67", "/history/private"),
	GameDetail("9ee0099fbe3d8db2a838a75cf42856dd", "/history/detail/VnNIaXN0b3J5RGV0YWlsLXUtYWVrcHRqYmd6N3hvdmd3emtvbW06QkFOS0FSQToyMDIzMDgyN1QyMTQ5NTBfYWY0MmRjYTItODY2ZC00YTY1LTg5ZDUtY2U3OGY1Y2U4YmQ4"),

	Salmon("01fb9793ad92f91892ea713410173260", "/coop"),
	SalmonDetail("379f0d9b78b531be53044bcac031b34b", ""),

	SplatNetShop("a43dd44899a09013bcfd29b4b13314ff", "/gesotown"),
	OwnedWeaponsAndGear("d29cd0c2b5e6bac90dd5b817914832f8", "/my_outfits/create"),
	Weapons("ebd88adbba13f09100f9326b1ec4c348", "/weapon_record"),

	RotationSchedules("f76dd61e08f4ce1d5d5b17762a243fec", "/schedule/bankara");

	@Getter
	private final static EnumSet<S3RequestKey> onlineBattles = EnumSet.of(Regular, Anarchy, XRank, Challenge, Private); // EnumSet.of(Latest, Regular, Anarchy, Private); //

	private final String key;
	private final String path;
}
