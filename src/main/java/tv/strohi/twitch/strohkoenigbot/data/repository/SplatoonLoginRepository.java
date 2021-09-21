package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.SplatoonLogin;

import java.util.List;

@Repository
public interface SplatoonLoginRepository extends CrudRepository<SplatoonLogin, Long> {
	List<SplatoonLogin> findById(long id);
}
