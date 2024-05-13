package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player;

import lombok.NonNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player.Splatoon3Nameplate;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3NameplateRepository extends CrudRepository<Splatoon3Nameplate, Long> {
	@NonNull List<Splatoon3Nameplate> findAllByOwned(boolean owned);

	@NonNull Optional<Splatoon3Nameplate> findById(long id);

	@NonNull Optional<Splatoon3Nameplate> findByApiId(String apiId);

	@NonNull List<Splatoon3Nameplate> findByOwnedAndPosted(boolean owned, boolean posted);
}
