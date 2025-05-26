package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsSpecialWeapon;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsSpecialWeaponRepository extends CrudRepository<Splatoon3VsSpecialWeapon, Long> {
	@NotNull Optional<Splatoon3VsSpecialWeapon> findByApiId(String apiId);

	@NotNull List<Splatoon3VsSpecialWeapon> findAll();
}
