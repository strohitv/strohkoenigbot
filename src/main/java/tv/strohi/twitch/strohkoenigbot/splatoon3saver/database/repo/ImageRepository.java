package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageRepository extends CrudRepository<Image, Long> {
	Optional<Image> findById(long id);

	Optional<Image> findByUrl(String imageUrl);

	List<Image> findByFilePathNullAndFailedDownloadCountLessThanEqual(int maxFailedDownloadCount);
}
