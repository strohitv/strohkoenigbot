package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "image")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Image {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String url;

	private String filePath;
}
