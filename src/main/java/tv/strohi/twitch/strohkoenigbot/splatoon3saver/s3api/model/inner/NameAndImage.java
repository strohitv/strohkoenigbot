package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// @Accessors(fluent = true)
public class NameAndImage implements Serializable {
	private String name;
	private Image image;
}
