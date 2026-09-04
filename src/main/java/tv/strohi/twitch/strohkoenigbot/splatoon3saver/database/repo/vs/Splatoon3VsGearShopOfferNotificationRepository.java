package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearShopOfferNotification;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsGearShopOfferNotificationRepository extends CrudRepository<Splatoon3VsGearShopOfferNotification, Long> {
	@NotNull Optional<Splatoon3VsGearShopOfferNotification> findByIdAndAccountId(long id, long accountId);

	@NotNull List<Splatoon3VsGearShopOfferNotification> findAll();

	@NotNull List<Splatoon3VsGearShopOfferNotification> findAllByAccountIdOrderById(long accountId);
}
