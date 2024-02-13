package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsSpecialWeapon;

import javax.persistence.Cacheable;

@Cacheable(false)
@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class SpecialWinCount {
	private Splatoon3VsSpecialWeapon specialWeapon;
	private int winCount;

	public SpecialWinCount(Splatoon3VsSpecialWeapon specialWeapon, long winCount) {
		this.specialWeapon = specialWeapon;
		this.winCount = Long.valueOf(winCount).intValue();
	}
}
