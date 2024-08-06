package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model;

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
public class DoubledEntries {
	private String apiId;
	private int count;

	public DoubledEntries(String apiId, long count) {
		this.apiId = apiId;
		this.count = Long.valueOf(count).intValue();
	}
}
