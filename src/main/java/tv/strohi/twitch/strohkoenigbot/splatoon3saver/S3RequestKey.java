package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;

@AllArgsConstructor
@Getter
public enum S3RequestKey {
	Home("dba47124d5ec3090c97ba17db5d2f4b3"),
	Latest("7d8b560e31617e981cf7c8aa1ca13a00"),
	Regular("f6e7e0277e03ff14edfef3b41f70cd33"),
	Anarchy("c1553ac75de0a3ea497cdbafaa93e95b"),
	Private("38e0529de8bc77189504d26c7a14e0b8"),
	GameDetail("2b085984f729cd51938fc069ceef784a"),
	Salmon("817618ce39bcf5570f52a97d73301b30"),
	SalmonDetail("f3799a033f0a7ad4b1b396f9a3bafb1e");

	@Getter
	private final static EnumSet<S3RequestKey> onlineBattles = EnumSet.of(Regular, Anarchy, Private); // EnumSet.of(Latest, Regular, Anarchy, Private); //

	private final String key;
}
