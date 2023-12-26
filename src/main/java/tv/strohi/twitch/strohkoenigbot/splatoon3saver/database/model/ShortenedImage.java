package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "shortened_image")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortenedImage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String imageUrl;

	private String imageFilePath;
}
