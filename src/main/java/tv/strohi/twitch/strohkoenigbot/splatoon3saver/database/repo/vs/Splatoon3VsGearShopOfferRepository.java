package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearShopOffer;

import java.time.Instant;
import java.util.List;

@Repository
public interface Splatoon3VsGearShopOfferRepository extends CrudRepository<Splatoon3VsGearShopOffer, Long> {
	List<Splatoon3VsGearShopOffer> findByAddedAt(Instant addedAt);

	List<Splatoon3VsGearShopOffer> findTop5ByGearAndAddedAtAfter(Splatoon3VsGear gear, Instant addedAtAfter);

	void deleteAllByAddedAt(Instant addedAt);
}
