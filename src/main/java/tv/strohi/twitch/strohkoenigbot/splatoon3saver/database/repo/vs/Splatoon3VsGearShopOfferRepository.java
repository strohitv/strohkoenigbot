package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearShopOffer;

import java.time.Instant;
import java.util.List;

@Repository
public interface Splatoon3VsGearShopOfferRepository extends CrudRepository<Splatoon3VsGearShopOffer, Long> {
	List<Splatoon3VsGearShopOffer> findByAddedAt(Instant addedAt);

	void deleteAllByAddedAt(Instant addedAt);
}
