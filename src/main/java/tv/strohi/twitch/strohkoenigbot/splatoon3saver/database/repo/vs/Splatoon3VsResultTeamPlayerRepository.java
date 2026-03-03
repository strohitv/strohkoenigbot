package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import feign.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsResultTeamPlayerRepository extends CrudRepository<Splatoon3VsResultTeamPlayer, Long> {
	Optional<Splatoon3VsResultTeamPlayer> findByResultIdAndTeamOrderAndPlayerId(long resultId, int teamOrder, long playerId);

	List<Splatoon3VsResultTeamPlayer> findByPlayerId(long playerId);
	List<Splatoon3VsResultTeamPlayer> findByNameContainsIgnoreCaseOrNameIdContains(String search, String nameId);

	@Query("SELECT count(*) \n" +
		"FROM splatoon_3_vs_result_team_player rtp \n" +
		"WHERE rtp.playerId = :playerId \n" +
		"GROUP BY rtp.playerId")
	Long getGameCountsWithPlayer(Long playerId);

	@Query("SELECT count(*) \n" +
		"FROM splatoon_3_vs_result_team_player rtp \n" +
		"WHERE rtp.isMyself = true and rtp.headGear.id = :headGearId \n" +
		"GROUP BY rtp.playerId")
	Long getGameCountOfOwnHeadGearId(@Param("headGearId") Long headGearId);

	@Query("SELECT count(*) \n" +
		"FROM splatoon_3_vs_result_team_player rtp \n" +
		"WHERE rtp.isMyself = true and rtp.clothingGear.id = :clothingGearId \n" +
		"GROUP BY rtp.playerId")
	Long getGameCountOfOwnClothingGearId(@Param("clothingGearId") Long clothingGearId);

	@Query("SELECT count(*) \n" +
		"FROM splatoon_3_vs_result_team_player rtp \n" +
		"WHERE rtp.isMyself = true and rtp.shoesGear.id = :shoesGearId \n" +
		"GROUP BY rtp.playerId")
	Long getGameCountOfOwnShoesGearId(@Param("shoesGearId") Long shoesGearId);
}
