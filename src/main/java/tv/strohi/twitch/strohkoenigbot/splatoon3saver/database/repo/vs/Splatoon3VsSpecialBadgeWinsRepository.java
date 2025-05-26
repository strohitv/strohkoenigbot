package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsSpecialBadgeWins;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsSpecialBadgeWinsRepository extends CrudRepository<Splatoon3VsSpecialBadgeWins, Long> {
	@NotNull List<Splatoon3VsSpecialBadgeWins> findAll();

	@Query(value = "SELECT max(wins.statDay)" +
		" FROM splatoon_3_vs_special_badge_wins wins")
	@NotNull Optional<LocalDate> findMaxDate();

	@NotNull Optional<Splatoon3VsSpecialBadgeWins> findBySpecialWeaponIdAndStatDay(long specialWeaponId, LocalDate day);
}
