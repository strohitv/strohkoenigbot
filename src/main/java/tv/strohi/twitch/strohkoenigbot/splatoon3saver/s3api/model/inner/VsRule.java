package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
// @Accessors(fluent = true)
public class VsRule implements Serializable {
	private String name;
	private String rule;
	private String id;
}
