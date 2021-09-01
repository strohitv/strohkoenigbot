package tv.strohi.twitch.strohkoenigbot.data.model;

import javax.persistence.*;

@Entity
@Table(name = "command")
@Cacheable(false)
public class Command {
	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "command")
	private String command;

	@Column(name = "role")
	private String role;

	public Command() {
	}

	public Command(long id, String command, String role) {
		this.id = id;
		this.command = command;
		this.role = role;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	@Override
	public String toString() {
		return "Command{" +
				"id=" + id +
				", command='" + command + '\'' +
				", role='" + role + '\'' +
				'}';
	}
}
