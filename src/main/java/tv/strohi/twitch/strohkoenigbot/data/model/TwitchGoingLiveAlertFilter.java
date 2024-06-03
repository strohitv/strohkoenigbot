package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.*;

import javax.persistence.Cacheable;
import java.util.List;

@Cacheable(false)
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
public class TwitchGoingLiveAlertFilter {
	private List<String> includeFilters;
}
