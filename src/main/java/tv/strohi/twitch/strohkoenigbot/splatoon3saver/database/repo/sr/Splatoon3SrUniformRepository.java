package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrUniform;

import java.util.Optional;

@Repository
public interface Splatoon3SrUniformRepository extends CrudRepository<Splatoon3SrUniform, Long> {
	Optional<Splatoon3SrUniform> findByApiId(String apiId);
}
