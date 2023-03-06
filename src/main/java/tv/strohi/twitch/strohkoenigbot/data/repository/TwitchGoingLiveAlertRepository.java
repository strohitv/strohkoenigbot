package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchGoingLiveAlert;

import java.util.List;
import java.util.Optional;

@Repository
public interface TwitchGoingLiveAlertRepository extends CrudRepository<TwitchGoingLiveAlert, Long> {

	@NotNull List<TwitchGoingLiveAlert> findAll();

	TwitchGoingLiveAlert findById(long id);

	List<TwitchGoingLiveAlert> findByTwitchChannelName(String channelName);
	Optional<TwitchGoingLiveAlert> findByTwitchChannelNameAndGuildIdAndChannelId(String channelName, long guildId, long channelId);
}
