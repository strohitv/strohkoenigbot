package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.Splatoon3SrGrade;

import java.util.Optional;

@Repository
public interface Splatoon3SrGradeRepository extends CrudRepository<Splatoon3SrGrade, Long> {
	Optional<Splatoon3SrGrade> findById(long id);
}
