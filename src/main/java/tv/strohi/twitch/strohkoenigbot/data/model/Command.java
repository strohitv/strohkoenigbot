package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "command")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Command {
	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "command")
	private String command;

	@Column(name = "role")
	private String role;
}
