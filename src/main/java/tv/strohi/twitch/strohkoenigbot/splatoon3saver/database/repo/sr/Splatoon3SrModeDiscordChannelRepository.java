package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrModeDiscordChannel;

import java.util.Optional;

@Repository
public interface Splatoon3SrModeDiscordChannelRepository extends CrudRepository<Splatoon3SrModeDiscordChannel, Long> {
	Optional<Splatoon3SrModeDiscordChannel> findById(long id);
}
