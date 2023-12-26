package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Configuration {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String configName;

	private String configValue;
}
