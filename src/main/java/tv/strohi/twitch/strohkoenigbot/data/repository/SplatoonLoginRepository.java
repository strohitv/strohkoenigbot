package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.SplatoonLogin;

import java.util.List;

@Repository
public interface SplatoonLoginRepository extends CrudRepository<SplatoonLogin, Long> {
	@NotNull List<SplatoonLogin> findAll();

	SplatoonLogin findById(long id);

	List<SplatoonLogin> findByCookie(String cookie);
}
