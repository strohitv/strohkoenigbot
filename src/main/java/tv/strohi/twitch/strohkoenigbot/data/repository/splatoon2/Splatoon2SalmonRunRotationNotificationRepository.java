package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2SalmonRunRotationNotification;

import java.util.List;

@Repository
public interface Splatoon2SalmonRunRotationNotificationRepository extends CrudRepository<Splatoon2SalmonRunRotationNotification, Long> {
	@NotNull List<Splatoon2SalmonRunRotationNotification> findAll();

	Splatoon2SalmonRunRotationNotification findById(long id);

	Splatoon2SalmonRunRotationNotification findByIdAndAccountIdOrderById(long id, long accountId);
	@NotNull List<Splatoon2SalmonRunRotationNotification> findByAccountIdOrderById(long accountId);
}
