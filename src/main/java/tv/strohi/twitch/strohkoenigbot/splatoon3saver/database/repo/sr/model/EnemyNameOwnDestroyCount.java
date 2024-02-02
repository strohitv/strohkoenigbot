package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.persistence.Cacheable;

@Cacheable(false)
@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class EnemyNameOwnDestroyCount {
	private String name;

	private int ownDestroyCount;

	public EnemyNameOwnDestroyCount(String name, long ownDestroyCount) {
		this.name = name;
		this.ownDestroyCount = Long.valueOf(ownDestroyCount).intValue();
	}
}
