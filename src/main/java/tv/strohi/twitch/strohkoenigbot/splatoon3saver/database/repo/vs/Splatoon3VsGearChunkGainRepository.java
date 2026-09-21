package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGear;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsGearChunkGain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsGearChunkGainRepository extends CrudRepository<Splatoon3VsGearChunkGain, Long> {
	Optional<Splatoon3VsGearChunkGain> findByReceivedAtBetweenAndGearAndPreviousExperienceAndNewExperience(Instant before, Instant after, Splatoon3VsGear gear, int previousExp, int newExp);

	List<Splatoon3VsGearChunkGain> findByReceivedAtAfter(Instant receivedAtAfter);

	@Query(value = "SELECT count(*)" +
		" FROM splatoon_3_vs_gear_chunk_gain gear_gain" +
		" WHERE gear_gain.gear = :gear")
	Long getTotalChunkGainOfGear(Splatoon3VsGear gear);
}
