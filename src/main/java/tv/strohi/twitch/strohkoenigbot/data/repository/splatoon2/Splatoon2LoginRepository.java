package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2Login;

import java.util.List;

@Repository
public interface Splatoon2LoginRepository extends CrudRepository<Splatoon2Login, Long> {
	@NotNull List<Splatoon2Login> findAll();

	Splatoon2Login findById(long id);

	List<Splatoon2Login> findByCookie(String cookie);
}
