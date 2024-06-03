package tv.strohi.twitch.strohkoenigbot.data.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import javax.persistence.*;

@Entity
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class TwitchGoingLiveAlert {
	private static final ObjectMapper mapper = new ObjectMapper();

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String twitchChannelName;

	private long guildId;

	private long channelId;

	private String notificationMessage;

	@Lob
	private String filters = null;

	public TwitchGoingLiveAlertFilter getFiltersAsObject() {
		if (filters == null) {
			return new TwitchGoingLiveAlertFilter();
		}

		try {
			return mapper.readValue(filters, TwitchGoingLiveAlertFilter.class);
		} catch (JsonProcessingException e) {
			log.error("could not parse filters", e);
			return new TwitchGoingLiveAlertFilter();
		}
	}

	public void setFilters(TwitchGoingLiveAlertFilter filters) {
		try {
			this.filters = mapper.writeValueAsString(filters);
		} catch (JsonProcessingException e) {
			log.error("could not write filters", e);
		}
	}
}
