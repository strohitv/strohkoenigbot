package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.*;

import javax.persistence.Cacheable;
import java.util.ArrayList;
import java.util.List;

@Cacheable(false)
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TwitchGoingLiveAlertFilter {
	private ArrayList<String> includeFilters;

	public List<String> getIncludeFilters() {
		if (includeFilters == null) {
			includeFilters = new ArrayList<>();
		}

		return includeFilters;
	}
}
