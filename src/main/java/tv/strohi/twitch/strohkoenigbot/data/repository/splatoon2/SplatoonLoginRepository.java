package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.SplatoonLogin;

import java.util.List;

@Repository
public interface SplatoonLoginRepository extends CrudRepository<SplatoonLogin, Long> {
	@NotNull List<SplatoonLogin> findAll();

	SplatoonLogin findById(long id);

	List<SplatoonLogin> findByCookie(String cookie);
}
